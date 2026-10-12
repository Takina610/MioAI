package com.mio.ai.bot.agent;

import com.mio.ai.bot.model.dto.AttachmentItem;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.service.SkillRuntime;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import com.mio.ai.framework.zagent.AgentEngine;
import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationHydrator;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.history.TodoItem;
import com.mio.ai.framework.zagent.hooks.AgentHooks;
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
    private final List<Skill> skills;
    private final AgentUsageLogService agentUsageLogService;
    private final ToolCallLogService toolCallLogService;
    private final AgentMessageService agentMessageService;
    private final com.mio.ai.bot.service.ChatFileService chatFileService;
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

    /** 用户主动中断标记：stop() 置位，引擎取消信号据此熔断步循环 */
    private volatile boolean stopped;

    /** 轮次执行线程：stop() 据此打断模型流/工具等待 */
    private volatile Thread turnThread;

    /** 编辑重发场景：本轮 user 行的版本组锚（沿用被编辑轮次的组） */
    private volatile Long userGroupSeq;

    /** 本轮 MCP 装配失败摘要（控制器注入）：非空时会话开头发瞬态系统提示 */
    private volatile List<String> mcpFailures;

    /** 本轮知识库检索命中摘要（控制器注入）：回答实际引用时在收尾落引用行 */
    private volatile List<Map<String, Object>> kbGroups;

    public void setUserGroupSeq(Long userGroupSeq) {
        this.userGroupSeq = userGroupSeq;
    }

    public void setMcpFailures(List<String> mcpFailures) {
        this.mcpFailures = mcpFailures;
    }

    public void setKbGroups(List<Map<String, Object>> kbGroups) {
        this.kbGroups = kbGroups;
    }

    public MioBot(ChatModel chatModel,
                  ToolsetFactory toolsetFactory,
                  List<ToolCallback> mcpTools,
                  List<Skill> skills,
                  AgentUsageLogService agentUsageLogService,
                  ToolCallLogService toolCallLogService,
                  AgentMessageService agentMessageService,
                  com.mio.ai.bot.service.ChatFileService chatFileService,
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
        this.skills = skills == null ? List.of() : skills;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.agentMessageService = agentMessageService;
        this.chatFileService = chatFileService;
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
        // MCP 工具描述表：tool_use 行附带工具描述（前端展示名称与描述，对齐内置工具行）
        Map<String, String> mcpToolDesc = new LinkedHashMap<>();
        for (ToolCallback callback : this.mcpTools) {
            try {
                mcpToolDesc.put(callback.getToolDefinition().name(),
                        callback.getToolDefinition().description());
            } catch (Exception ignored) {
                // 单个回调定义异常不阻断装配
            }
        }
        this.channel.setMcpToolDesc(mcpToolDesc);
        // 技能名表：工具读取 skills/<id>/ 文件时在工具行标注来源技能（技能是否使用由模型按需决定）
        Map<String, String> skillNames = new LinkedHashMap<>();
        for (Skill skill : this.skills) {
            skillNames.put(String.valueOf(skill.getId()), skill.getName());
        }
        this.channel.setSkillNames(skillNames);
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

        // 专用轮次池：不占 ForkJoinPool.commonPool（阻塞任务会饿死全 JVM 的 CompletableFuture）
        if (!TurnExecutors.execute(() -> runTurnTask(userPrompt, knowledgeContext,
                persistUserMessage, sseEmitter))) {
            completeWithBusy(sseEmitter);
        }

        sseEmitter.onTimeout(() -> log.warn("MioBot SSE connection timeout, chatId={}", chatId));
        return sseEmitter;
    }

    private void runTurnTask(String userPrompt, String knowledgeContext, boolean persistUserMessage,
                             SseEmitter sseEmitter) {
        turnThread = Thread.currentThread();
        long startTime = System.currentTimeMillis();
        ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                channel::heartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ToolRegistry registry = null;
        try {
            // MCP 装配失败项在会话开头明说（瞬态提示不落库，重载后不保留）
            if (mcpFailures != null && !mcpFailures.isEmpty()) {
                String joined = String.join("；", mcpFailures);
                channel.notice("以下 MCP 工具本轮不可用：" + (joined.length() > 300
                        ? joined.substring(0, 300) + "..." : joined));
            }
            ConversationHydrator.Hydrated hydrated = hydrate();
            ConversationState state = new ConversationState();
            state.entries().addAll(hydrated.entries());
            state.setTodos(hydrated.todos());

            BotAgentEvents events = new BotAgentEvents(state);
            // 技能物化到沙箱 skills/ 目录，并生成注入上下文的技能清单段（无技能/沙箱不可用时为 null）
            SandboxFs fs = toolsetFactory.sandboxFs();
            String skillsContext = SkillRuntime.materialize(skills, fs);
            // 工具调用钩子：审计落库从事件通道解耦为可插拔钩子（引擎核心不再硬编码横切关注点）
            AgentHooks toolHooks = AgentHooks.chain(List.of(new ToolAuditHook()));
            ToolsetFactory.RunContext runContext = new ToolsetFactory.RunContext(
                    chatId, fs, mcpTools,
                    com.mio.ai.framework.zagent.task.BackgroundTasks.instance(), toolHooks);
            ContextBuilder contextBuilder = new ContextBuilder(
                    customSystemPrompt, knowledgeContext, skillsContext, runContext.fs(), modelName,
                    userId != null ? agentId : null);
            AgentEngine engine = new AgentEngine(chatModel, config, reasoningEffort, contextBuilder,
                    state, chatId, events, runContext.tasks());
            // 用户中断：引擎步间/重试等待处检查取消信号，命中即停（不走兜底抢救）
            engine.setCancelSignal(() -> stopped);

            if ("/compact".equals(userPrompt.strip())) {
                runManualCompact(engine, hydrated);
            } else {
                Subagents launcher = new Subagents(chatModel, config, reasoningEffort,
                        toolsetFactory, runContext, events, modelName);
                registry = toolsetFactory.build(chatId, state, mcpTools, null, launcher, events, toolHooks);
                engine.bindTools(registry);

                boolean appendUserEntry = persistUserMessage || state.entries().isEmpty();
                if (persistUserMessage) {
                    channel.setUserAttachments(attachments);
                    channel.persistDisplay("user", userPrompt, null, userGroupSeq);
                }
                // WIP 行：轮次开始即落库，此后每个模型步增量刷新（进程崩溃后过程可恢复）
                channel.beginAssistantWip();
                // 轮次开始前刷新脱离式后台任务：沙箱侧已完结的补状态并入队 task-notification
                runContext.tasks().refreshDetached(chatId, runContext.fs());
                java.util.Set<String> outputsBefore = snapshotOutputs(runContext.fs());
                engine.runTurn(buildModelPrompt(userPrompt, runContext.fs()), appendUserEntry);
                collectOutputs(runContext.fs(), outputsBefore);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            channel.abortRunningTools();
            // 知识库引用行：回答实际引用了检索片段才出现（引用驱动的"使用"信号，弱相似噪声不显示）
            channel.emitKbCitations(kbGroups);
            boolean persisted = channel.persistDisplay("assistant", null, durationMs);
            channel.finishAssistantWip(persisted);
            channel.usage((int) engine.inputTokens(), (int) engine.outputTokens(), durationMs);
            channel.done();
            complete(sseEmitter);
        } catch (Throwable e) {
            if (isStoppedTurn(e)) {
                // 用户主动中断：悬挂工具块收尾 + 部分过程落库（带中断标记）+ stopped 事件。
                // 不落 durationMs——前端据 stopped 标记显示"已停止"，不渲染"已完成"折叠行
                log.info("MioBot 轮次被用户中断, chatId={}", chatId);
                channel.abortRunningTools();
                channel.stopped();
                boolean persisted = channel.persistDisplay("assistant", null, null);
                channel.finishAssistantWip(persisted);
                complete(sseEmitter);
            } else {
                log.error("MioBot 执行异常, chatId={}", chatId, e);
                channel.abortRunningTools();
                boolean persisted = channel.persistDisplay("assistant", null, System.currentTimeMillis() - startTime);
                channel.finishAssistantWip(persisted);
                channel.error("执行出错：" + e.getMessage());
                complete(sseEmitter);
            }
        } finally {
            heartbeat.cancel(false);
            if (registry != null) {
                registry.shutdown();
            }
            turnThread = null;
            // 清掉中断标记：轮次线程归还线程池，残留标记会让下一轮任务误判为中断
            Thread.interrupted();
            if (onFinish != null) {
                try {
                    onFinish.run();
                } catch (Exception ignored) {
                    // 清理回调失败不影响主流程
                }
            }
        }
    }

    /** 用户主动中断：置停止标记并打断轮次线程（模型流 blockLast/工具 future.get/重试 sleep 均响应中断） */
    public void stop() {
        this.stopped = true;
        Thread t = this.turnThread;
        if (t != null) {
            t.interrupt();
        }
    }

    /** 断线重连：把新连接挂到事件通道上，回放 lastSeq 之后的事件并续播直播 */
    public void attachReplay(SseEmitter emitter, long lastSeq) {
        channel.attachReplay(emitter, lastSeq);
    }

    /** 重连回放是否已对本轮停用（缓冲超上限） */
    public boolean isReplayDisabled() {
        return channel.isReplayDisabled();
    }

    /** 本轮是否为用户中断收尾：stop 标记/引擎停止异常/线程中断（沿 cause 链查） */
    private boolean isStoppedTurn(Throwable error) {
        if (stopped || error instanceof com.mio.ai.framework.zagent.TurnStoppedException) {
            return true;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** 轮次池打满：明确告知用户而非静默排队 */
    private void completeWithBusy(SseEmitter sseEmitter) {
        try {
            SseStreams.sendTyped(sseEmitter, SseChunk.content("error",
                    "系统繁忙：当前对话任务数已达上限，请稍后重试。").fields(), 1);
            SseStreams.sendTyped(sseEmitter, SseChunk.done().fields(), 2);
        } catch (Exception ignored) {
            // 连接已断开
        }
        complete(sseEmitter);
        log.warn("轮次线程池已满，拒绝任务, chatId={}", chatId);
        // 轮次任务未跑，onFinish 不会经 finally 触发：这里补上，否则 ACTIVE_CHATS 占用泄漏
        if (onFinish != null) {
            try {
                onFinish.run();
            } catch (Exception ignored) {
                // 清理回调失败不影响主流程
            }
        }
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
                if (chatFileService != null) {
                    // 台账登记产出文件：会话删除时按台账清目录（登记失败不影响交付）
                    chatFileService.registerOutputs(chatId, userId, agentId, produced);
                }
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
        return hydrateConversation(agentMessageService, chatId, userId);
    }

    /** 会话历史水合（轮次与上下文容量估算共用）：游客/无记录/回读失败均回空历史 */
    public static ConversationHydrator.Hydrated hydrateConversation(
            AgentMessageService agentMessageService, String chatId, Long userId) {
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
        private long lastLoggedCached;

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
        public void toolResult(String id, String name, String content, boolean error, long durationMs) {
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
        public void stepFinished(int step, long inputTokens, long outputTokens, long cachedInputTokens,
                                 boolean cachedInputKnown,
                                 List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
            logUsageDelta(inputTokens - lastLoggedInput, outputTokens - lastLoggedOutput,
                    cachedInputKnown ? cachedInputTokens - lastLoggedCached : null);
            lastLoggedInput = inputTokens;
            lastLoggedOutput = outputTokens;
            lastLoggedCached = cachedInputTokens;
            // 每步增量落库：崩溃/被杀时刷新页面能看到已流出的部分过程
            channel.flushAssistantWip();
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

        private void logUsageDelta(long inputDelta, long outputDelta, Long cachedDelta) {
            // 游客不记用量：agent_usage_log.user_id NOT NULL，游客 userId=null 插入必失败
            if (agentUsageLogService == null || userId == null
                    || (inputDelta <= 0 && outputDelta <= 0 && (cachedDelta == null || cachedDelta <= 0))) {
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
            // 上游未回传缓存明细时保持 NULL（命中率按未知处理，不误报 0）
            usageLog.setCachedTokens(cachedDelta == null ? null
                    : (int) Math.min(Integer.MAX_VALUE, Math.max(0, cachedDelta)));
            agentUsageLogService.logUsage(usageLog);
        }
    }

    /**
     * 工具调用审计钩子（afterToolCall）：工具调用日志从引擎事件通道解耦为插件。
     * 注册表在每次工具执行（含失败/超时/子代理）后回调，真实状态/耗时/结果在此落库。
     */
    private final class ToolAuditHook implements AgentHooks {

        /** 工具日志的结果预览上限（output_result 为 text 列，完整结果已在 agent_message.blocks） */
        private static final int RESULT_LOG_CAP = 2_000;

        @Override
        public void afterToolCall(ToolCall call, com.mio.ai.framework.zagent.tools.ToolRegistry.Executed result,
                                  com.mio.ai.framework.zagent.tools.ToolContext context) {
            if (toolCallLogService == null) {
                return;
            }
            try {
                ToolCallLog toolCallLog = new ToolCallLog();
                toolCallLog.setAgentId(agentId);
                toolCallLog.setUserId(userId);
                toolCallLog.setConversationId(parseConversationId());
                toolCallLog.setToolId(0L);
                toolCallLog.setName(call.name());
                toolCallLog.setInputParams(validJsonOrNull(call.argumentsJson()));
                toolCallLog.setStatus(resolveToolStatus(result.error(), result.content()));
                toolCallLog.setExecutionTime((int) Math.min(Integer.MAX_VALUE,
                        Math.max(0, result.durationMs())));
                String content = result.content();
                if (result.error()) {
                    toolCallLog.setErrorMsg(content == null ? "" : content);
                } else if (content != null && content.length() > RESULT_LOG_CAP) {
                    toolCallLog.setOutputResult(content.substring(0, RESULT_LOG_CAP)
                            + "\n[...truncated at " + RESULT_LOG_CAP + " chars...]");
                } else {
                    toolCallLog.setOutputResult(content);
                }
                toolCallLogService.logToolCall(toolCallLog);
            } catch (Exception ignored) {
                // 审计失败不影响工具链
            }
        }

        /** 表字典：1-成功 0-失败 2-超时（超时信封文案来自 ToolRegistry 的超时分支） */
        private int resolveToolStatus(boolean error, String content) {
            if (!error) {
                return 1;
            }
            return content != null && content.contains("exceeded timeout") ? 2 : 0;
        }

        /** input_params 是 JSON 列：模型参数理论上恒为 JSON，畸形时退 null 保落库 */
        private String validJsonOrNull(String args) {
            if (args == null || args.isBlank()) {
                return null;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode ignore =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(args);
                return args;
            } catch (Exception e) {
                return null;
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
