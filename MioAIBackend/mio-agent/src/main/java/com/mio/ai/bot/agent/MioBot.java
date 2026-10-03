package com.mio.ai.bot.agent;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.framework.plan.AgentPlan;
import com.mio.ai.framework.plan.PlanningTool;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MioBot：MioAI 唯一的智能体，具备完整 Agent 能力。
 * <p>运行在"思考 → 行动 → 观察"的流式循环里：每轮模型输出边生成边推送
 * （推理增量 thinking / 正文增量 answer），模型发起的工具调用由
 * {@link ToolCallingManager} 手动执行（tool_call / tool_result 事件实时可见），
 * 结果回填上下文后进入下一轮，直到模型不再调用工具（给出最终回答）或达到步数上限。
 * <p>规划：复杂任务通过 {@link PlanningTool} 维护任务清单，清单变化以 plan 事件推送前端；
 * 记忆：会话历史经 {@link ChatMemory} 跨请求持久化，运行开始时回读为上下文。
 * <p>由调用方按请求创建实例，不作为 Spring Bean 管理。
 */
@Slf4j
public class MioBot {

    /** agent 表中 MioBot 的固定 ID（日志与会话记录沿用该关联） */
    public static final long AGENT_ID = 1L;

    /** 单轮任务的最大思考-行动循环步数（防止失控循环） */
    private static final int MAX_STEPS = 30;

    /** SSE 工具结果事件的预览长度：完整结果已进入模型上下文，前端只需可读摘要 */
    private static final int TOOL_RESULT_PREVIEW_LENGTH = 400;

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final AgentUsageLogService agentUsageLogService;
    private final ToolCallLogService toolCallLogService;

    private final String chatId;
    private final Long userId;
    private final ToolCallback[] tools;

    private final AgentPlan plan = new AgentPlan();
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    // 当前运行的 SSE 连接与事件序号
    private SseEmitter emitter;
    private final AtomicInteger seq = new AtomicInteger();

    public MioBot(ChatModel chatModel,
                  ChatMemory chatMemory,
                  ToolCallback[] builtInTools,
                  List<ToolCallback> mcpTools,
                  AgentUsageLogService agentUsageLogService,
                  ToolCallLogService toolCallLogService,
                  String chatId,
                  Long userId) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.chatId = chatId;
        this.userId = userId;
        this.tools = concatTools(builtInTools, mcpTools, ToolCallbacks.from(new PlanningTool(plan))[0]);
    }

    /**
     * 执行一轮对话任务，流式返回全过程事件。
     *
     * @param userPrompt      用户输入
     * @param knowledgeContext 知识库检索命中的上下文（可为空），注入系统提示词
     */
    public SseEmitter run(String userPrompt, String knowledgeContext) {
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        this.emitter = sseEmitter;
        plan.setChangeListener(this::emitPlan);

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            try {
                List<Message> messages = new ArrayList<>(loadHistory());
                UserMessage userMessage = new UserMessage(userPrompt);
                messages.add(userMessage);
                persist(userMessage);

                String systemPrompt = buildSystemPrompt(knowledgeContext);
                boolean finished = false;

                for (int step = 1; step <= MAX_STEPS && !finished; step++) {
                    Prompt prompt = new Prompt(messages, buildChatOptions())
                            .augmentSystemMessage(systemPrompt + planSection());

                    StreamTurnCollector collector = new StreamTurnCollector(this::emitThinkingDelta, this::emitAnswerDelta);
                    chatModel.stream(prompt).doOnNext(collector::accept).blockLast();

                    ChatResponse response = collector.build();
                    Usage usage = response.getMetadata().getUsage();
                    if (usage != null) {
                        totalInputTokens += usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                        totalOutputTokens += usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
                    }
                    logUsage(response, step);

                    AssistantMessage assistant = response.getResult().getOutput();
                    persist(assistant);

                    // 模型不再调用工具 = 最终回答已流式输出完毕，任务结束
                    if (!assistant.hasToolCalls()) {
                        finished = true;
                        break;
                    }

                    emitToolCalls(assistant.getToolCalls());
                    ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, response);
                    ToolResponseMessage toolResponseMessage =
                            (ToolResponseMessage) toolResult.conversationHistory()
                                    .get(toolResult.conversationHistory().size() - 1);

                    // 上下文回填：用户消息 + 助手工具调用 + 工具结果（剔除 augment 注入的系统消息，避免逐轮堆积）
                    messages.add(assistant);
                    messages.add(toolResponseMessage);
                    persist(toolResponseMessage);
                    emitToolResults(toolResponseMessage);
                }

                if (!finished) {
                    emitAnswerDelta("\n\n（已达到单轮任务的最大执行步数，以上是目前的执行结果，可以继续提问让我接着完成。）");
                }
                emit(SseChunk.usage(totalInputTokens, totalOutputTokens,
                        System.currentTimeMillis() - startTime).fields());
                emit(SseChunk.done().fields());
                complete();
            } catch (Exception e) {
                log.error("MioBot 执行异常", e);
                emit(SseChunk.content("error", "执行出错：" + e.getMessage()).fields());
                complete();
            }
        });

        sseEmitter.onTimeout(() -> log.warn("MioBot SSE connection timeout, chatId={}", chatId));
        return sseEmitter;
    }

    /**
     * 回读会话历史作为本轮上下文（修复旧实现跨请求失忆的问题）；失败时降级为空历史
     */
    private List<Message> loadHistory() {
        try {
            List<Message> history = chatMemory.get(chatId);
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            log.warn("回读会话记忆失败，从空上下文开始: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String buildSystemPrompt(String knowledgeContext) {
        StringBuilder sb = new StringBuilder("""
                你是 MioBot，MioAI 的智能助手，具备完整的 Agent 能力：自主规划任务、调用工具、根据结果迭代执行，直到真正完成用户的需求。

                # 工作方式
                - 你运行在"思考 → 行动 → 观察"的循环里：每轮可先简述你要做什么，然后调用工具，拿到结果后决定下一步。
                - 涉及多步骤的任务，开始时先调用 managePlan 创建任务清单，随进度更新每个步骤的状态。
                - 工具调用失败时分析原因重试或换方案，不要静默放弃，也不要编造结果。
                - 需要事实性、时效性信息（新闻、价格、文档细节等）时主动使用搜索/抓取工具，不要凭记忆猜测。
                - 最终回答用 Markdown 排版：重点加粗、列表分点、代码放代码块、对比数据用表格。
                - 使用中文回答，语气自然干练，不写套话。

                # 结束条件
                当所有子任务完成、任务清单全部标记 done 后，直接输出面向用户的最终回答（不再调用任何工具）。\
                """);
        if (StrUtil.isNotBlank(knowledgeContext)) {
            sb.append("\n\n").append(knowledgeContext);
        }
        return sb.toString();
    }

    /** 把当前任务清单注入系统提示词，让模型始终"看得见"计划进度 */
    private String planSection() {
        String planText = plan.render();
        if (planText.isEmpty()) {
            return "";
        }
        return "\n\n【当前任务清单】\n" + planText
                + "\n请按清单推进：完成的步骤及时标记 done，正在做的标记 in_progress。";
    }

    /**
     * 以模型默认配置（yml 里的模型名）为基础挂上可用工具。
     * 必须用 OpenAiChatOptions：模型内部会强转 prompt.options，且裸 builder
     * 默认 model=gpt-5-mini，会覆盖 yml 里的 dashscope 模型名导致 404。
     */
    private OpenAiChatOptions buildChatOptions() {
        if (chatModel.getDefaultOptions() instanceof OpenAiChatOptions defaults) {
            return defaults.mutate()
                    .toolCallbacks(List.of(tools))
                    .build();
        }
        return OpenAiChatOptions.builder()
                .toolCallbacks(List.of(tools))
                .build();
    }

    private ToolCallback[] concatTools(ToolCallback[] builtIn, List<ToolCallback> mcp, ToolCallback planning) {
        List<ToolCallback> all = new ArrayList<>();
        if (builtIn != null) {
            all.addAll(List.of(builtIn));
        }
        if (mcp != null) {
            all.addAll(mcp);
        }
        all.add(planning);
        return all.toArray(ToolCallback[]::new);
    }

    private void persist(Message message) {
        if (chatMemory == null || chatId == null || message == null) {
            return;
        }
        try {
            chatMemory.add(chatId, message);
        } catch (Exception e) {
            log.warn("会话记忆写入失败: {}", e.getMessage());
        }
    }

    private void logUsage(ChatResponse response, int step) {
        if (agentUsageLogService == null) {
            return;
        }
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(AGENT_ID);
        usageLog.setUserId(userId);
        usageLog.setConversationId(parseConversationId());
        usageLog.setStatus(1);
        usageLog.setCreateTime(new Date());
        Usage usage = response.getMetadata().getUsage();
        if (usage != null) {
            if (usage.getPromptTokens() != null) {
                usageLog.setInputTokens(usage.getPromptTokens().intValue());
            }
            if (usage.getCompletionTokens() != null) {
                usageLog.setOutputTokens(usage.getCompletionTokens().intValue());
            }
        }
        agentUsageLogService.logUsage(usageLog);
    }

    private void emitToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            emit(SseChunk.toolCall(toolCall.id(), toolCall.name(), toolCall.arguments()).fields());
            logToolCall(toolCall);
        }
    }

    private void logToolCall(AssistantMessage.ToolCall toolCall) {
        if (toolCallLogService == null) {
            return;
        }
        ToolCallLog toolCallLog = new ToolCallLog();
        toolCallLog.setAgentId(AGENT_ID);
        toolCallLog.setUserId(userId);
        toolCallLog.setConversationId(parseConversationId());
        toolCallLog.setToolId(0L);
        toolCallLog.setName(toolCall.name());
        toolCallLog.setInputParams(toolCall.arguments());
        toolCallLog.setStatus(1);
        toolCallLog.setCreateTime(new Date());
        toolCallLogService.logToolCall(toolCallLog);
    }

    private void emitToolResults(ToolResponseMessage toolResponseMessage) {
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            emit(SseChunk.toolResult(response.id(), response.name(),
                    truncate(response.responseData(), TOOL_RESULT_PREVIEW_LENGTH)).fields());
        }
    }

    /** 任务清单变化（PlanningTool 执行中触发）：推送结构化步骤列表，前端渲染为计划面板 */
    private void emitPlan() {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (AgentPlan.Step step : plan.snapshot()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", step.index());
            item.put("description", step.description());
            item.put("status", step.status().name().toLowerCase());
            steps.add(item);
        }
        emit(SseChunk.plan(steps).fields());
    }

    private void emitThinkingDelta(String delta) {
        emit(SseChunk.delta("thinking", delta).fields());
    }

    private void emitAnswerDelta(String delta) {
        emit(SseChunk.delta("answer", delta).fields());
    }

    /** 发送一条信封事件（连接断开时静默跳过，不影响执行与落库） */
    private void emit(Map<String, Object> fields) {
        if (emitter != null) {
            SseStreams.sendTyped(emitter, fields, seq.incrementAndGet());
        }
    }

    private void complete() {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 连接已被容器关闭
        }
    }

    private Long parseConversationId() {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
