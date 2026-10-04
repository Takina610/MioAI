package com.mio.ai.bot.agent;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MioBot：ZCode 风格的流式 Agent 引擎，MioBot（内置）与用户自定义智能体共用。
 * <p>一次 run = 一个助手回合：正文/推理增量实时推送，工具调用在生成阶段即出现并流式输出参数，
 * 执行结果以 tool_result 回填；循环"模型 → 工具 → 模型"直到模型不再调用工具。
 * <p>系统提示词 = 身份（自定义或默认）+ Agent 纪律（classpath:prompts/miobot-system.txt）
 * + 环境信息（当前时间/沙箱可用性，模型无需再查时间）+ 知识库上下文 + 任务清单。
 * <p>由调用方按请求创建实例，不作为 Spring Bean 管理。
 */
@Slf4j
public class MioBot {

    /** agent 表中 MioBot 的固定 ID（日志与会话记录沿用该关联） */
    public static final long AGENT_ID = 1L;

    /** 单轮任务的最大思考-行动循环步数（防止失控循环） */
    private static final int MAX_STEPS = 30;

    /** 心跳间隔：小于常见代理/网关的空闲超时，保证长工具执行期间连接存活 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    /** Agent 循环纪律（外置资源文件，参考 opencode 的 prompt 组织方式） */
    private static final String AGENT_DISCIPLINE = loadDiscipline();

    /** 达到步数上限前的最后一轮提示：禁止再调工具，强制输出总结 */
    private static final String STEP_LIMIT_HINT =
            "[system] 已达到单轮最大执行步数。本轮禁止再调用任何工具，请立即基于已获得的信息输出总结与后续建议。";

    /** 心跳调度器（daemon 单线程，所有 run 共享） */
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

    private final String chatId;
    private final Long userId;
    private final Long agentId;
    private final String baseSystemPrompt;
    private final boolean sandboxAvailable;
    private final ToolCallback[] tools;

    private final AgentPlan plan = new AgentPlan();
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final BotEventChannel channel;

    /** 任务结束（含异常）回调：控制器借此解除会话占用（防重复执行闸门） */
    private volatile Runnable onFinish;

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
                  String baseSystemPrompt,
                  boolean sandboxAvailable) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.chatId = chatId;
        this.userId = userId;
        this.agentId = agentId != null ? agentId : AGENT_ID;
        this.baseSystemPrompt = baseSystemPrompt;
        this.sandboxAvailable = sandboxAvailable;
        this.tools = concatTools(builtInTools, mcpTools,
                ToolCallbacks.from(new PlanningTool(plan))[0]);
        this.channel = new BotEventChannel(agentMessageService, chatId, userId, agentId);
    }

    /**
     * 执行一轮对话任务，流式返回全过程事件。
     *
     * @param userPrompt        用户输入
     * @param knowledgeContext  知识库检索命中的上下文（可为空），注入系统提示词
     * @param persistUserMessage 为 false 时不重复落库用户消息（重新生成场景：截断后重发）
     */
    public SseEmitter run(String userPrompt, String knowledgeContext, boolean persistUserMessage) {
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        channel.bind(sseEmitter);
        plan.setChangeListener(this::emitPlan);

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            int totalInputTokens = 0;
            int totalOutputTokens = 0;
            ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                    channel::heartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
            try {
                List<Message> messages = new ArrayList<>(loadHistory());
                UserMessage userMessage = new UserMessage(userPrompt);
                messages.add(userMessage);
                if (persistUserMessage) {
                    persist(userMessage);
                    channel.persistDisplay("user", userPrompt, null);
                }

                String systemPrompt = buildSystemPrompt(knowledgeContext);
                boolean finished = false;

                for (int step = 1; step <= MAX_STEPS && !finished; step++) {
                    if (step == MAX_STEPS) {
                        messages.add(new UserMessage(STEP_LIMIT_HINT));
                    }
                    Prompt prompt = new Prompt(messages, buildChatOptions())
                            .augmentSystemMessage(systemPrompt + planSection());

                    StreamTurnCollector collector = new StreamTurnCollector(
                            channel::thinkingDelta, channel::answerDelta,
                            channel::toolUse, channel::toolArgs);
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

                    // 上下文回填：助手工具调用 + 工具结果（剔除 augment 注入的系统消息，避免逐轮堆积）
                    messages.add(assistant);
                    messages.add(toolResponseMessage);
                    channel.toolResults(toolResponseMessage);
                }

                if (!finished) {
                    channel.answerDelta("\n\n（已达到单轮任务的最大执行步数，以上是目前的执行结果，可以继续提问让我接着完成。）");
                }
                finalizePlan();
                long durationMs = System.currentTimeMillis() - startTime;
                channel.persistDisplay("assistant", null, durationMs);
                channel.usage(totalInputTokens, totalOutputTokens, durationMs);
                channel.done();
                complete(sseEmitter);
            } catch (Exception e) {
                log.error("MioBot 执行异常", e);
                channel.persistDisplay("assistant", null, System.currentTimeMillis() - startTime);
                channel.error("执行出错：" + e.getMessage());
                complete(sseEmitter);
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
     * 回读会话历史作为本轮上下文（跨请求不失忆）；失败时降级为空历史
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
            sb.append("你是 MioBot，MioAI 的智能助手，具备完整的 Agent 能力：自主规划任务、调用工具、根据结果迭代执行，直到真正完成用户的需求。");
        }
        sb.append("\n\n").append(AGENT_DISCIPLINE);
        sb.append("\n\n").append(environmentSection());
        if (StrUtil.isNotBlank(knowledgeContext)) {
            sb.append("\n\n").append(knowledgeContext);
        }
        return sb.toString();
    }

    /** 环境信息块：当前时间直接注入，模型无需再调用工具查询 */
    private String environmentSection() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String[] weekDays = {"一", "二", "三", "四", "五", "六", "日"};
        String time = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " 星期" + weekDays[now.getDayOfWeek().getValue() - 1];
        StringBuilder sb = new StringBuilder("# 环境信息\n- 当前时间：").append(time).append("（北京时间）");
        if (sandboxAvailable) {
            sb.append("\n- 沙箱：可用 runInSandbox / writeSandboxFile 在 Linux 沙箱服务器上执行命令与写文件，")
              .append("适合运行代码、处理数据、验证想法；工作目录内文件互相独立于本服务。");
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

    /** 任务清单变化：把结构化步骤推给前端渲染为计划面板 */
    private void emitPlan() {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (AgentPlan.Step step : plan.snapshot()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", step.index());
            item.put("description", step.description());
            item.put("status", step.status().name().toLowerCase());
            steps.add(item);
        }
        channel.plan(steps);
    }

    /**
     * 收尾同步：模型给出最终回答却没把清单余下步骤标记完成时统一补成已完成，
     * 保证前端清单与结论一致。
     */
    private void finalizePlan() {
        for (AgentPlan.Step step : plan.snapshot()) {
            if (step.status() != AgentPlan.StepStatus.DONE && step.status() != AgentPlan.StepStatus.FAILED) {
                plan.updateStatus(step.index(), AgentPlan.StepStatus.DONE);
            }
        }
    }

    private void complete(SseEmitter emitter) {
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

    private static String loadDiscipline() {
        try (InputStream in = MioBot.class.getResourceAsStream("/prompts/miobot-system.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            log.warn("加载 Agent 纪律提示词失败，使用内置兜底", e);
        }
        return """
                # 工作方式
                - 你运行在"思考 → 行动 → 观察"的循环里：每轮可先简述你要做什么，然后调用工具，拿到结果后决定下一步。
                - 工具调用失败时分析原因重试或换方案，不要静默放弃，也不要编造结果。
                - 需要事实性、时效性信息（新闻、价格、文档细节等）时主动使用搜索/抓取工具，不要凭记忆猜测。
                - 最终回答用 Markdown 排版，使用中文，语气自然干练。
                - 当所有子任务完成、能给出完整回答时，直接输出最终回答（不再调用任何工具）。""";
    }
}
