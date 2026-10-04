package com.mio.ai.bot.agent;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.common.utils.JacksonUtil;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MioBot：ZCode 风格的流式 Agent 引擎，MioBot（内置）与用户自定义智能体共用。
 * <p>一次 run = 一个助手回合，由事件块流构成：正文/推理增量实时推送，
 * 工具调用在生成阶段即出现（tool_use）并流式输出参数（tool_args），
 * 执行结果以 tool_result 回填；循环"模型 → 工具 → 模型"直到模型不再调用工具。
 * <p>保活：整个运行期间每 {@link #HEARTBEAT_INTERVAL_SECONDS} 秒发送一次 heartbeat 事件，
 * 长工具执行（如 PDF 生成+上传）期间连接不会因无数据被代理/看门狗掐断。
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

    /** 心跳间隔：小于常见代理/网关的空闲超时，保证长工具执行期间连接存活 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    /** 心跳调度器（daemon 单线程，所有 run 共享；任务本身只做一次轻量发送） */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mio-bot-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final AgentUsageLogService agentUsageLogService;
    private final ToolCallLogService toolCallLogService;
    private final AgentMessageService agentMessageService;

    private final String chatId;
    private final Long userId;
    private final Long agentId;
    private final String baseSystemPrompt;
    private final ToolCallback[] tools;

    private final AgentPlan plan = new AgentPlan();
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    // 展示持久化：与本轮 SSE 事件同构的内容块（文本/思考/工具）+ 最终清单快照 + 耗时
    private final List<Map<String, Object>> displayBlocks = new ArrayList<>();

    /** 任务结束（含异常）回调：控制器借此解除会话占用（防重复执行闸门） */
    private volatile Runnable onFinish;
    private List<Map<String, Object>> displayPlan;

    // 当前运行的 SSE 连接与事件序号
    private SseEmitter emitter;
    private final AtomicInteger seq = new AtomicInteger();

    /**
     * @param baseSystemPrompt 自定义智能体的身份提示词（空则用 MioBot 默认身份）；
     *                         Agent 循环纪律（工作方式/结束条件）始终追加，保证每个智能体都有完整 Agent 能力
     */
    public MioBot(ChatModel chatModel,
                  ChatMemory chatMemory,
                  ToolCallback[] builtInTools,
                  List<ToolCallback> mcpTools,
                  AgentUsageLogService agentUsageLogService,
                  ToolCallLogService toolCallLogService,
                  AgentMessageService agentMessageService,
                  String chatId,
                  Long userId,
                  Long agentId,
                  String baseSystemPrompt) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.agentMessageService = agentMessageService;
        this.chatId = chatId;
        this.userId = userId;
        this.agentId = agentId != null ? agentId : AGENT_ID;
        this.baseSystemPrompt = baseSystemPrompt;
        this.tools = concatTools(builtInTools, mcpTools, ToolCallbacks.from(new PlanningTool(plan))[0]);
    }

    /**
     * 执行一轮对话任务，流式返回全过程事件。
     *
     * @param userPrompt       用户输入
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
            ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                    this::emitHeartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
            try {
                List<Message> messages = new ArrayList<>(loadHistory());
                UserMessage userMessage = new UserMessage(userPrompt);
                messages.add(userMessage);
                persist(userMessage);
                persistDisplayMessage("user", userPrompt, null);

                String systemPrompt = buildSystemPrompt(knowledgeContext);
                boolean finished = false;

                for (int step = 1; step <= MAX_STEPS && !finished; step++) {
                    Prompt prompt = new Prompt(messages, buildChatOptions())
                            .augmentSystemMessage(systemPrompt + planSection());

                    StreamTurnCollector collector = new StreamTurnCollector(
                            this::emitThinkingDelta, this::emitAnswerDelta,
                            this::emitToolUse, this::emitToolArgs);
                    chatModel.stream(prompt).doOnNext(collector::accept).blockLast();

                    ChatResponse response = collector.build();
                    Usage usage = response.getMetadata().getUsage();
                    if (usage != null) {
                        totalInputTokens += usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                        totalOutputTokens += usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
                    }
                    logUsage(response, step);

                    AssistantMessage assistant = response.getResult().getOutput();

                    // 模型不再调用工具 = 最终回答已流式输出完毕，任务结束
                    if (!assistant.hasToolCalls()) {
                        persist(assistant);
                        finished = true;
                        break;
                    }

                    // JdbcChatMemoryRepository 不支持工具调用消息：落一份"纯文本"副本保留叙述，供跨请求历史回看
                    if (StrUtil.isNotBlank(assistant.getText())) {
                        persist(new AssistantMessage(assistant.getText()));
                    }

                    logToolCalls(assistant.getToolCalls());
                    ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, response);
                    ToolResponseMessage toolResponseMessage =
                            (ToolResponseMessage) toolResult.conversationHistory()
                                    .get(toolResult.conversationHistory().size() - 1);

                    // 上下文回填：用户消息 + 助手工具调用 + 工具结果（剔除 augment 注入的系统消息，避免逐轮堆积）
                    messages.add(assistant);
                    messages.add(toolResponseMessage);
                    emitToolResults(toolResponseMessage);
                }

                if (!finished) {
                    emitAnswerDelta("\n\n（已达到单轮任务的最大执行步数，以上是目前的执行结果，可以继续提问让我接着完成。）");
                }
                finalizePlan();
                long durationMs = System.currentTimeMillis() - startTime;
                persistDisplayMessage("assistant", null, durationMs);
                emit(SseChunk.usage(totalInputTokens, totalOutputTokens, durationMs).fields());
                emit(SseChunk.done().fields());
                complete();
            } catch (Exception e) {
                log.error("MioBot 执行异常", e);
                persistDisplayMessage("assistant", null, System.currentTimeMillis() - startTime);
                emit(SseChunk.content("error", "执行出错：" + e.getMessage()).fields());
                complete();
            } finally {
                heartbeat.cancel(false);
                if (onFinish != null) {
                    try {
                        onFinish.run();
                    } catch (Exception ignored) {
                        // 清理回调失败不影响主流程
                    }
                }
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
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(baseSystemPrompt)) {
            sb.append(baseSystemPrompt.strip());
        } else {
            sb.append("""
                    你是 MioBot，MioAI 的智能助手，具备完整的 Agent 能力：自主规划任务、调用工具、根据结果迭代执行，直到真正完成用户的需求。\
                    """);
        }
        sb.append("""

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
        usageLog.setAgentId(agentId);
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

    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCallLogService == null) {
            return;
        }
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            ToolCallLog toolCallLog = new ToolCallLog();
            toolCallLog.setAgentId(agentId);
            toolCallLog.setUserId(userId);
            toolCallLog.setConversationId(parseConversationId());
            toolCallLog.setToolId(0L);
            toolCallLog.setName(toolCall.name());
            toolCallLog.setInputParams(toolCall.arguments());
            toolCallLog.setStatus(1);
            toolCallLog.setCreateTime(new Date());
            toolCallLogService.logToolCall(toolCallLog);
        }
    }

    private void emitToolResults(ToolResponseMessage toolResponseMessage) {
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            String preview = truncate(response.responseData(), TOOL_RESULT_PREVIEW_LENGTH);
            completeDisplayTool(response.id(), response.name(), preview);
            emit(SseChunk.toolResult(response.id(), response.name(), preview).fields());
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
        displayPlan = steps;
        emit(SseChunk.plan(steps).fields());
    }

    /**
     * 收尾同步：模型给出最终回答却没把清单余下步骤标记完成时，
     * 统一补成已完成（每次 updateStatus 触发 emitPlan），保证前端清单与结论一致。
     */
    private void finalizePlan() {
        for (AgentPlan.Step step : plan.snapshot()) {
            if (step.status() != AgentPlan.StepStatus.DONE && step.status() != AgentPlan.StepStatus.FAILED) {
                plan.updateStatus(step.index(), AgentPlan.StepStatus.DONE);
            }
        }
    }

    private void emitThinkingDelta(String delta) {
        appendDisplayText("thinking", delta);
        emit(SseChunk.delta("thinking", delta).fields());
    }

    private void emitAnswerDelta(String delta) {
        appendDisplayText("text", delta);
        emit(SseChunk.delta("answer", delta).fields());
    }

    private void emitToolUse(String id, String tool) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool");
        if (id != null && !id.isBlank()) {
            block.put("id", id);
        }
        block.put("tool", tool);
        block.put("args", "");
        block.put("status", "running");
        displayBlocks.add(block);
        emit(SseChunk.toolUse(id, tool).fields());
    }

    private void emitToolArgs(String id, String delta) {
        // 优先按 id 配对；无 id 的兼容端点则落到最后一个 running 工具块
        Map<String, Object> target = null;
        if (id != null && !id.isBlank()) {
            for (int i = displayBlocks.size() - 1; i >= 0; i--) {
                Map<String, Object> b = displayBlocks.get(i);
                if ("tool".equals(b.get("type")) && id.equals(b.get("id"))) {
                    target = b;
                    break;
                }
            }
        }
        if (target == null) {
            for (int i = displayBlocks.size() - 1; i >= 0; i--) {
                Map<String, Object> b = displayBlocks.get(i);
                if ("tool".equals(b.get("type")) && "running".equals(b.get("status"))) {
                    target = b;
                    break;
                }
            }
        }
        if (target != null) {
            target.put("args", String.valueOf(target.get("args")) + delta);
        }
        emit(SseChunk.toolArgs(id, delta).fields());
    }

    private void completeDisplayTool(String id, String tool, String result) {
        for (int i = displayBlocks.size() - 1; i >= 0; i--) {
            Map<String, Object> b = displayBlocks.get(i);
            if (!"tool".equals(b.get("type")) || !"running".equals(b.get("status"))) {
                continue;
            }
            boolean idMatch = id != null && id.equals(b.get("id"));
            boolean nameMatch = tool != null && tool.equals(b.get("tool"));
            if (idMatch || nameMatch) {
                b.put("status", "done");
                b.put("result", result);
                return;
            }
        }
        // 没有配对的调用块（异常兜底）：补一个已完成块保证结果不丢
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool");
        block.put("tool", tool);
        block.put("result", result);
        block.put("status", "done");
        displayBlocks.add(block);
    }

    /** 展示块文本增量：合并同类末块（与前端渲染逻辑同构） */
    private void appendDisplayText(String type, String delta) {
        if (!displayBlocks.isEmpty()) {
            Map<String, Object> last = displayBlocks.get(displayBlocks.size() - 1);
            if (type.equals(last.get("type"))) {
                last.put("text", String.valueOf(last.get("text")) + delta);
                return;
            }
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put("text", delta);
        displayBlocks.add(block);
    }

    /** 展示持久化：完整 blocks/plan/duration 落 agent_message（游客不落库） */
    private void persistDisplayMessage(String role, String text, Long durationMs) {
        if (agentMessageService == null || userId == null) {
            return;
        }
        try {
            AgentMessageDO row = new AgentMessageDO();
            row.setConversationId(chatId);
            row.setAgentId(agentId);
            row.setUserId(userId);
            row.setRole(role);
            if (text != null) {
                List<Map<String, Object>> blocks = new ArrayList<>();
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "text");
                block.put("text", text);
                blocks.add(block);
                row.setBlocks(JacksonUtil.writeValueAsString(blocks));
            } else {
                row.setBlocks(JacksonUtil.writeValueAsString(displayBlocks));
                if (displayPlan != null && !displayPlan.isEmpty()) {
                    row.setPlan(JacksonUtil.writeValueAsString(displayPlan));
                }
                row.setDurationMs(durationMs != null ? durationMs.intValue() : null);
            }
            agentMessageService.append(row);
        } catch (Exception e) {
            log.warn("展示消息落库失败: {}", e.getMessage());
        }
    }

    private void emitHeartbeat() {
        emit(SseChunk.heartbeat().fields());
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

    public void setOnFinish(Runnable onFinish) {
        this.onFinish = onFinish;
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
