package com.mio.ai.bot.agent;

import com.mio.ai.bot.model.dto.AttachmentItem;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import com.mio.ai.framework.zagent.AgentEngine;
import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationHydrator;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.history.TodoItem;
import com.mio.ai.framework.zagent.subagent.Subagents;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import com.mio.ai.framework.zagent.tools.ToolRegistry;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MioBot：zagent 引擎（zcode AgentRuntime 对位移植）之上的产品化封装。
 * <p>职责：①把引擎事件翻译成 SSE 信封流（前端协议不变）；②agent_message 展示持久化
 * 与请求历史水合；③心跳保活与用量/工具日志。Agent 行为完全由 zagent 决定。
 */
@Slf4j
public class MioBot {

    /** agent 表中 MioBot 的固定 ID（日志与会话记录沿用该关联） */
    public static final long AGENT_ID = 1L;

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mio-bot-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final ChatModel chatModel;
    private final ToolsetFactory toolsetFactory;
    private final List<ToolCallback> mcpTools;
    private final AgentUsageLogService agentUsageLogService;
    private final ToolCallLogService toolCallLogService;
    private final AgentMessageService agentMessageService;
    private final SandboxFileTransfer fileTransfer;

    private final String chatId;
    private final Long userId;
    private final long agentId;
    private final String customSystemPrompt;
    private final String modelName;
    private final String reasoningEffort;
    private final AgentEngineConfig config;

    /** 本轮用户附件（可空）：落库展示 + 注入模型上下文 */
    private final List<AttachmentItem> attachments;

    private final BotEventChannel channel;

    /** 任务结束（含异常）回调：控制器借此解除会话占用 */
    private volatile Runnable onFinish;

    /** 编辑重发场景：本轮 user 行的版本组锚（沿用被编辑轮次的组） */
    private volatile Long userGroupSeq;

    public void setUserGroupSeq(Long userGroupSeq) {
        this.userGroupSeq = userGroupSeq;
    }

    public MioBot(ChatModel chatModel,
                  ToolsetFactory toolsetFactory,
                  List<ToolCallback> mcpTools,
                  AgentUsageLogService agentUsageLogService,
                  ToolCallLogService toolCallLogService,
                  AgentMessageService agentMessageService,
                  String chatId,
                  Long userId,
                  Long agentId,
                  String customSystemPrompt,
                  String modelName,
                  String reasoningEffort,
                  AgentEngineConfig config,
                  SandboxFileTransfer fileTransfer,
                  List<AttachmentItem> attachments) {
        this.chatModel = chatModel;
        this.toolsetFactory = toolsetFactory;
        this.mcpTools = mcpTools;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.agentMessageService = agentMessageService;
        this.chatId = chatId;
        this.userId = userId;
        this.agentId = agentId != null ? agentId : AGENT_ID;
        this.customSystemPrompt = customSystemPrompt;
        this.modelName = modelName;
        this.reasoningEffort = reasoningEffort;
        this.config = config;
        this.attachments = attachments == null ? List.of() : attachments;
        this.fileTransfer = fileTransfer;
        this.channel = new BotEventChannel(agentMessageService, chatId, userId, agentId);
    }

    /**
     * 执行一轮对话任务，流式返回全过程事件。
     *
     * @param userPrompt        用户输入（"/compact" 触发手动压缩）
     * @param knowledgeContext  知识库检索命中的上下文（可为空），以附件注入
     * @param persistUserMessage 为 false 时不重复落库用户消息（重新生成场景：截断后重发）
     */
    public SseEmitter run(String userPrompt, String knowledgeContext, boolean persistUserMessage) {
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        channel.bind(sseEmitter);

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                    channel::heartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
            ToolRegistry registry = null;
            try {
                ConversationHydrator.Hydrated hydrated = hydrate();
                ConversationState state = new ConversationState();
                state.entries().addAll(hydrated.entries());
                state.setTodos(hydrated.todos());

                BotAgentEvents events = new BotAgentEvents(state);
                ToolsetFactory.RunContext runContext = new ToolsetFactory.RunContext(
                        chatId, toolsetFactory.sandboxFs(), mcpTools,
                        com.mio.ai.framework.zagent.task.BackgroundTasks.instance());
                ContextBuilder contextBuilder = new ContextBuilder(
                        customSystemPrompt, knowledgeContext, runContext.fs(), modelName);
                AgentEngine engine = new AgentEngine(chatModel, config, reasoningEffort, contextBuilder,
                        state, chatId, events, runContext.tasks());

                if ("/compact".equals(userPrompt.strip())) {
                    runManualCompact(engine, hydrated);
                } else {
                    Subagents launcher = new Subagents(chatModel, config, reasoningEffort,
                            toolsetFactory, runContext, events, modelName);
                    registry = toolsetFactory.build(chatId, state, mcpTools, null, launcher, events);
                    engine.bindTools(registry);

                    boolean appendUserEntry = persistUserMessage || state.entries().isEmpty();
                    if (persistUserMessage) {
                        channel.setUserAttachments(attachments);
                        channel.persistDisplay("user", userPrompt, null, userGroupSeq);
                    }
                    java.util.Set<String> outputsBefore = snapshotOutputs(runContext.fs());
                    engine.runTurn(buildModelPrompt(userPrompt, runContext.fs()), appendUserEntry);
                    collectOutputs(runContext.fs(), outputsBefore);
                }

                long durationMs = System.currentTimeMillis() - startTime;
                channel.abortRunningTools();
                channel.persistDisplay("assistant", null, durationMs);
                channel.usage((int) engine.inputTokens(), (int) engine.outputTokens(), durationMs);
                channel.done();
                complete(sseEmitter);
            } catch (Exception e) {
                log.error("MioBot 执行异常, chatId={}", chatId, e);
                channel.abortRunningTools();
                channel.persistDisplay("assistant", null, System.currentTimeMillis() - startTime);
                channel.error("执行出错：" + e.getMessage());
                complete(sseEmitter);
            } finally {
                heartbeat.cancel(false);
                if (registry != null) {
                    registry.shutdown();
                }
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

    /** /compact：手动压缩（zcode executeManualCompact 对位），完成后以简短说明收尾 */
    private void runManualCompact(AgentEngine engine, ConversationHydrator.Hydrated hydrated) {
        if (hydrated.entries().size() < 4) {
            channel.answerDelta("The conversation is short enough; nothing to compact yet.");
            return;
        }
        String summary = engine.compactNow();
        if (summary == null) {
            channel.answerDelta("Compaction skipped: not enough conversation history to summarize.");
        }
    }

    /** 模型可见的用户输入：附件以 system-reminder 注入（路径 + 产出目录约定），展示正文保持原样 */
    private String buildModelPrompt(String userPrompt, SandboxFs fs) {
        if (attachments.isEmpty() || fs == null || fileTransfer == null || !fileTransfer.available()) {
            return userPrompt;
        }
        StringBuilder sb = new StringBuilder(userPrompt);
        sb.append("\n\n<system-reminder>\n# 用户附件\n用户随本条消息上传了文件（上传已在对话外完成，"
                + "文件就在沙箱工作区——不存在任何上传工具，不要尝试上传/下载，直接用文件工具读取）：\n");
        for (AttachmentItem item : attachments) {
            sb.append("- `").append(item.path()).append('`');
            String storedName = item.path().substring(item.path().lastIndexOf('/') + 1);
            if (item.name() != null && !item.name().isBlank() && !item.name().equals(storedName)) {
                sb.append("（原始文件名: ").append(item.name()).append('）');
            }
            if (item.size() > 0) {
                sb.append("，").append(humanSize(item.size()));
            }
            sb.append('\n');
        }
        sb.append("如需把处理后的文件交付给用户，写入 outputs/").append(SandboxFileTransfer.safeDir(chatId))
                .append("/ 目录（已创建）：本轮结束后其中的新文件会自动作为可下载附件展示给用户。\n</system-reminder>");
        return sb.toString();
    }

    /** 本轮产出目录（工作区内相对路径，chatId 已净化） */
    private String outputsDir() {
        return SandboxFileTransfer.OUTPUTS_ROOT + "/" + SandboxFileTransfer.safeDir(chatId);
    }

    /** 轮次开始：创建产出目录并快照既有文件（结束时的差集即本轮新产出） */
    private java.util.Set<String> snapshotOutputs(SandboxFs fs) {
        if (fileTransfer == null || !fileTransfer.available() || fs == null) {
            return null;
        }
        try {
            fileTransfer.ensureOutputDir(chatId);
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Map<String, Object> item : fileTransfer.listDir(outputsDir())) {
                names.add(String.valueOf(item.get("name")));
            }
            return names;
        } catch (Exception e) {
            log.warn("产出目录快照失败, chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /** 轮次结束：本轮新写入 outputs/&lt;chatId&gt;/ 的文件作为附件块推给前端（含落库） */
    private void collectOutputs(SandboxFs fs, java.util.Set<String> before) {
        if (fileTransfer == null || !fileTransfer.available() || fs == null || before == null) {
            return;
        }
        try {
            List<Map<String, Object>> produced = fileTransfer.listNew(outputsDir(), before);
            if (!produced.isEmpty()) {
                channel.attachments(produced);
            }
        } catch (Exception e) {
            log.warn("产出文件收集失败, chatId={}: {}", chatId, e.getMessage());
        }
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return Math.max(1, bytes / 1024) + " KB";
    }

    /** 冷启动水合：agent_message 展示行 → 请求历史 + 任务清单（游客/无记录时空历史） */
    private ConversationHydrator.Hydrated hydrate() {
        if (userId == null || agentMessageService == null) {
            return new ConversationHydrator.Hydrated(List.of(), List.of());
        }
        try {
            List<AgentMessageDO> rows = agentMessageService.listByConversation(chatId);
            List<ConversationHydrator.DisplayRow> displayRows = rows.stream()
                    .map(row -> new ConversationHydrator.DisplayRow(row.getRole(), row.getBlocks(), row.getPlan()))
                    .toList();
            return ConversationHydrator.hydrate(displayRows);
        } catch (Exception e) {
            log.warn("回读会话历史失败，从空上下文开始: {}", e.getMessage());
            return new ConversationHydrator.Hydrated(List.of(), List.of());
        }
    }

    /** 引擎事件 → SSE 信封 + 日志 */
    private final class BotAgentEvents implements AgentEvents {
        private final ConversationState state;
        private long lastLoggedInput;
        private long lastLoggedOutput;

        private BotAgentEvents(ConversationState state) {
            this.state = state;
        }

        @Override
        public void thinkingDelta(String delta) {
            channel.thinkingDelta(delta);
        }

        @Override
        public void answerDelta(String delta) {
            channel.answerDelta(delta);
        }

        @Override
        public void toolUse(String id, String name) {
            channel.toolUse(id, name);
        }

        @Override
        public void toolArgs(String id, String delta) {
            channel.toolArgs(id, delta);
        }

        @Override
        public void toolResult(String id, String name, String content) {
            channel.toolResult(id, name, content);
        }

        @Override
        public void todosChanged() {
            List<Map<String, Object>> steps = new java.util.ArrayList<>();
            for (TodoItem todo : state.todos()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", steps.size());
                item.put("description", todo.content());
                item.put("status", todo.displayStatus());
                steps.add(item);
            }
            channel.plan(steps);
        }

        @Override
        public void retryScheduled(int attempt, int maxAttempts, String reason) {
            log.warn("模型流瞬态失败，自动重试 {}/{}，chatId={}，reason={}",
                    attempt, maxAttempts, chatId, reason);
            channel.retryScheduled(attempt, maxAttempts, reason);
        }

        @Override
        public void stepFinished(int step, long inputTokens, long outputTokens,
                                 List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
            logUsageDelta(inputTokens - lastLoggedInput, outputTokens - lastLoggedOutput);
            lastLoggedInput = inputTokens;
            lastLoggedOutput = outputTokens;
            logToolCalls(toolCalls);
        }

        @Override
        public void compacted(String summary) {
            channel.persistCompact(summary);
            channel.answerDelta("\n\n[上下文已自动压缩：更早的对话已折叠为摘要，当前任务不受影响]\n\n");
        }

        @Override
        public void question(String id, List<Map<String, Object>> questions) {
            channel.question(id, questions);
        }

        @Override
        public void questionAnswered(String id, List<Map<String, Object>> answers) {
            channel.questionAnswered(id, answers);
        }

        private void logUsageDelta(long inputDelta, long outputDelta) {
            if (agentUsageLogService == null || (inputDelta <= 0 && outputDelta <= 0)) {
                return;
            }
            AgentUsageLog usageLog = new AgentUsageLog();
            usageLog.setAgentId(agentId);
            usageLog.setUserId(userId);
            usageLog.setConversationId(parseConversationId());
            usageLog.setStatus(1);
            usageLog.setCreateTime(new Date());
            usageLog.setInputTokens((int) Math.max(0, inputDelta));
            usageLog.setOutputTokens((int) Math.max(0, outputDelta));
            agentUsageLogService.logUsage(usageLog);
        }

        private void logToolCalls(List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
            if (toolCallLogService == null || toolCalls == null) {
                return;
            }
            for (var call : toolCalls) {
                ToolCallLog toolCallLog = new ToolCallLog();
                toolCallLog.setAgentId(agentId);
                toolCallLog.setUserId(userId);
                toolCallLog.setConversationId(parseConversationId());
                toolCallLog.setToolId(0L);
                toolCallLog.setName(call.name());
                toolCallLog.setInputParams(call.arguments());
                toolCallLog.setStatus(1);
                toolCallLog.setCreateTime(new Date());
                toolCallLogService.logToolCall(toolCallLog);
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
}
