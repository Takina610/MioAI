package com.mio.ai.framework.zagent.subagent;

import com.mio.ai.framework.zagent.AgentEngine;
import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.tools.delegation.DelegationExecutors;
import com.mio.ai.framework.zagent.tools.contract.ToolRegistry;
import com.mio.ai.framework.zagent.tools.contract.ToolsetFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子代理运行器（zcode runtime/methods/subagent.ts 的对位移植）：
 * 子运行时独立历史与上下文（只拿 prompt 文本），工具面按档案裁剪；
 * 工具调用以带前缀 id 镜像到父级事件流；后台模式入注册表并通知父会话。
 * <p>后台委派走 {@link DelegationExecutors#background()} 有界池：每个后台子代理都是一个
 * 能跑完整 AgentEngine 的线程，无界池在多轮累积后会把线程数推到不可控。
 */
@Slf4j
public final class Subagents implements SubagentLauncher {

    private static final ExecutorService POOL = DelegationExecutors.background();

    /**
     * 后台委派池的可替换引用：生产恒为 {@link DelegationExecutors#background()}。
     * 测试通过 {@link #useBackgroundExecutor} 注入会拒绝提交的池，以验证池满时
     * 已登记任务不会被遗留成永久 running 的幽灵任务。
     */
    private static volatile ExecutorService backgroundExecutor = POOL;

    static void useBackgroundExecutor(ExecutorService executor) {
        backgroundExecutor = executor == null ? POOL : executor;
    }

    static ExecutorService currentBackgroundExecutor() {
        return backgroundExecutor;
    }

    private final ChatModel chatModel;
    private final AgentEngineConfig config;
    private final String reasoningEffort;
    private final ToolsetFactory toolsetFactory;
    private final ToolsetFactory.RunContext runContext;
    private final AgentEvents parentEvents;
    private final String modelName;
    /** 群聊成员启动器（null = 普通会话，只有内置档案） */
    private final MemberLauncher memberLauncher;
    /** 群成员展示回调（null = 沿用子代理镜像呈现） */
    private final MemberPresenter memberPresenter;
    /** 群成员用量回调（null = 不回收）：成员委派结束后按成员身份上抛 token/耗时 */
    private final MemberUsageListener memberUsageListener;

    public Subagents(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                     ToolsetFactory toolsetFactory, ToolsetFactory.RunContext runContext,
                     AgentEvents parentEvents, String modelName) {
        this(chatModel, config, reasoningEffort, toolsetFactory, runContext, parentEvents, modelName,
                null, null, null);
    }

    public Subagents(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                     ToolsetFactory toolsetFactory, ToolsetFactory.RunContext runContext,
                     AgentEvents parentEvents, String modelName, MemberLauncher memberLauncher) {
        this(chatModel, config, reasoningEffort, toolsetFactory, runContext, parentEvents, modelName,
                memberLauncher, null, null);
    }

    public Subagents(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                     ToolsetFactory toolsetFactory, ToolsetFactory.RunContext runContext,
                     AgentEvents parentEvents, String modelName, MemberLauncher memberLauncher,
                     MemberPresenter memberPresenter) {
        this(chatModel, config, reasoningEffort, toolsetFactory, runContext, parentEvents, modelName,
                memberLauncher, memberPresenter, null);
    }

    public Subagents(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                     ToolsetFactory toolsetFactory, ToolsetFactory.RunContext runContext,
                     AgentEvents parentEvents, String modelName, MemberLauncher memberLauncher,
                     MemberPresenter memberPresenter, MemberUsageListener memberUsageListener) {
        this.chatModel = chatModel;
        this.config = config;
        this.reasoningEffort = reasoningEffort;
        this.toolsetFactory = toolsetFactory;
        this.runContext = runContext;
        this.parentEvents = parentEvents;
        this.modelName = modelName;
        this.memberLauncher = memberLauncher;
        this.memberPresenter = memberPresenter;
        this.memberUsageListener = memberUsageListener;
    }

    @Override
    public Result launch(String subagentType, String description, String prompt, boolean background) {
        // 群聊会话：成员名命中走成员路径；群内不提供内置档案（成员是平等的，没有隐藏子代理）
        if (memberLauncher != null) {
            MemberLauncher.MemberAgent member = memberLauncher.resolve(subagentType);
            if (member == null) {
                throw new IllegalStateException("subagent_type 必须使用系统提示成员名单里的成员名原文，收到: "
                        + subagentType);
            }
            return launchMember(member, description, prompt, background);
        }
        AgentProfiles.Profile profile = AgentProfiles.resolve(subagentType);
        String agentId = "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long startedAt = System.currentTimeMillis();
        if (background) {
            var task = runContext.tasks().register(runContext.chatId(), "agent",
                    description == null ? profile.name() : description);
            submitBackground(task, () -> runChild(profile, agentId, prompt));
            return new Result(agentId, profile.name(), prompt,
                    "Agent launched in background (" + profile.name() + "). You will be notified when it "
                            + "completes; use TaskOutput with task_id \"" + task.id + "\" to wait for it.",
                    0, 0, 0, task.id);
        }
        Outcome outcome = runChild(profile, agentId, prompt);
        return new Result(agentId, profile.name(), prompt, outcome.content(),
                outcome.toolUseCount(), outcome.totalTokens(), System.currentTimeMillis() - startedAt,
                null, outcome.failed());
    }

    /**
     * 群成员委派：前台阻塞/后台任务两种结构，成员执行以平等消息呈现（presenter）。
     * "正在输入"指示在重装配（MCP/知识库/技能，可能网络往返）之前下发——用户不用
     * 盯着空白等装配；装配失败转为成员失败态，与执行失败同一路径收尾。
     */
    private Result launchMember(MemberLauncher.MemberAgent member, String description, String prompt,
                                boolean background) {
        String agentId = "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long startedAt = System.currentTimeMillis();
        if (background) {
            var task = runContext.tasks().register(runContext.chatId(), "agent",
                    description == null ? member.name() : description);
            submitBackground(task, () -> runMemberWithPresence(member, agentId, prompt));
            return new Result(agentId, member.name(), prompt,
                    "Agent launched in background (" + member.name() + "). You will be notified when it "
                            + "completes; use TaskOutput with task_id \"" + task.id + "\" to wait for it.",
                    0, 0, 0, task.id);
        }
        Outcome outcome = runMemberWithPresence(member, agentId, prompt);
        return new Result(agentId, member.name(), prompt, outcome.content(),
                outcome.toolUseCount(), outcome.totalTokens(), System.currentTimeMillis() - startedAt,
                null, outcome.failed());
    }

    /** 成员执行全周期：started → 装配 → 运行 → finished（装配失败也走 finished(failed)） */
    private Outcome runMemberWithPresence(MemberLauncher.MemberAgent member, String agentId, String prompt) {
        if (memberPresenter != null) {
            memberPresenter.started(agentId, member.id(), member.name(), member.icon());
        }
        long startedAt = System.currentTimeMillis();
        MemberLauncher.MemberPlan plan;
        try {
            plan = memberLauncher.prepare(member, prompt, runContext.fs());
        } catch (Exception e) {
            log.warn("群成员装配失败 ({}): {}", member.name(), e.getMessage());
            if (memberPresenter != null) {
                memberPresenter.finished(agentId, true);
            }
            notifyMemberUsage(member.id(), 0, 0, System.currentTimeMillis() - startedAt, true);
            return new Outcome("(Member preparation failed: " + e.getMessage() + ")", 0, 0, 0, true);
        }
        Outcome outcome = runPlan(plan, agentId, prompt);
        if (memberPresenter != null) {
            memberPresenter.finished(agentId, outcome.failed());
        }
        notifyMemberUsage(member.id(), outcome.inputTokens(), outcome.outputTokens(),
                System.currentTimeMillis() - startedAt, outcome.failed());
        return outcome;
    }

    private void notifyMemberUsage(long memberAgentId, long inputTokens, long outputTokens,
                                   long durationMs, boolean failed) {
        if (memberUsageListener == null) {
            return;
        }
        try {
            memberUsageListener.onUsage(memberAgentId, inputTokens, outputTokens, durationMs, failed);
        } catch (Exception e) {
            log.warn("群成员用量回调失败 ({}): {}", memberAgentId, e.getMessage());
        }
    }

    /**
     * 提交后台委派。池满时任务已在注册表里登记，必须就地收尾为失败——
     * 否则会留下一条永远 "running" 的幽灵任务（TaskOutput 永远查不到结果、
     * TaskStop 也停不掉），这是有界池相对旧无界池新引入的失败面。
     */
    private void submitBackground(com.mio.ai.framework.zagent.task.BackgroundTasks.Task task,
                                  java.util.function.Supplier<Outcome> body) {
        try {
            backgroundExecutor.execute(() -> {
                task.runnerThread = Thread.currentThread();
                Outcome outcome;
                try {
                    outcome = runWithBudget(body);
                } catch (Throwable fatal) {
                    // 兜底收尾：任何逃逸出 runWithBudget 的异常（含 Error）都必须让任务落地，
                    // 否则会留下永久 running 的幽灵任务——TaskOutput 查不到、TaskStop 停不掉。
                    log.warn("后台委派异常终止 ({}): {}", task.id, fatal.toString());
                    runContext.tasks().complete(task, true, null,
                            "(Background delegation aborted: " + fatal.getMessage() + ")");
                    return;
                }
                runContext.tasks().complete(task, outcome.failed(), 0, outcome.content());
            });
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            log.warn("后台委派池已满，任务直接失败: {}", task.id);
            runContext.tasks().complete(task, true, null,
                    "(Delegation rejected: the background agent pool is full. Retry later.)");
            throw rejected;
        }
    }

    /** 一次子代理/成员运行的结果（包级可见：runWithBudget 的回归测试需要它） */
    record Outcome(String content, int toolUseCount, long inputTokens, long outputTokens, boolean failed) {

        private long totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /** 后台委派看门狗：单例调度器，任务结束即取消，不随委派数增长 */
    private static final java.util.concurrent.ScheduledExecutorService BUDGET_WATCHDOG =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "zagent-delegate-budget");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 后台委派的墙钟预算：前台由 {@link org.springframework.ai.tool.ToolCallback} 侧的
     * future 超时兜住，后台没有等待者，必须自己起看门狗——否则一个卡住的成员会永久
     * 占住有界池的一个线程，直到后端重启。
     * <p>包级可见：中断标记清理只能在"刚返回那一刻"观察（JDK 的 ThreadPoolExecutor 会在
     * 任务边界自动清标记，从池外永远看不到残留），回归测试需直接驱动本方法。
     */
    Outcome runWithBudget(java.util.function.Supplier<Outcome> body) {
        long budgetMs = config.delegationTimeoutMs();
        if (budgetMs <= 0) {
            return body.get();
        }
        Thread runner = Thread.currentThread();
        java.util.concurrent.ScheduledFuture<?> watchdog = BUDGET_WATCHDOG.schedule(() -> {
            log.warn("后台委派超出预算 {}ms，中断执行线程", budgetMs);
            runner.interrupt();
        }, budgetMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        try {
            return body.get();
        } catch (Exception e) {
            if (isInterrupted(e)) {
                return new Outcome("(Delegation exceeded its time budget of " + (budgetMs / 1000)
                        + "s and was aborted.)", 0, 0, 0, true);
            }
            throw e;
        } finally {
            watchdog.cancel(false);
            // 线程会被有界池复用：残留的中断标记会让下一个委派在第一步就自杀。
            // 看门狗存在"任务刚返回、中断才送达"的竞态，所以无条件清一次。
            Thread.interrupted();
        }
    }

    /** 中断类失败：直查标记与异常链（引擎会把 InterruptedException 包成 TurnStoppedException） */
    private static boolean isInterrupted(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException
                    || current instanceof com.mio.ai.framework.zagent.TurnStoppedException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** 群成员运行：成员自己的 MCP 工具/知识库命中/技能清单，全量内置工具面。
     *  有展示回调时成员正文直接外流（平等消息），工具/思考不打扰主会话流 */
    private Outcome runPlan(MemberLauncher.MemberPlan plan, String agentId, String prompt) {
        String systemPrompt = plan.systemPrompt() == null || plan.systemPrompt().isBlank()
                ? AgentProfiles.generalPurpose().systemPrompt()
                : plan.systemPrompt().strip();
        if (plan.description() != null && !plan.description().isBlank()) {
            systemPrompt += "\n\n# 角色描述\n" + plan.description().strip();
        }
        // 成员在本会话的线程上下文：同一成员多次被委派时接续自己此前的发言，
        // 不必让群主每次把前情复述一遍（Thread 订阅的会话内对位）
        if (plan.threadContext() != null && !plan.threadContext().isBlank()) {
            systemPrompt += "\n\n# 你在本会话中的先前发言\n" + plan.threadContext().strip();
        }
        MemberStreamingEvents events = new MemberStreamingEvents(memberPresenter, agentId);
        Outcome outcome = runEngine(systemPrompt, List.of("*"), plan.mcpTools(),
                plan.knowledgeContext(), plan.skillsContext(), agentId, prompt, events);
        return new Outcome(outcome.content(), events.toolUseCount.get(),
                outcome.inputTokens(), outcome.outputTokens(), outcome.failed());
    }

    private Outcome runChild(AgentProfiles.Profile profile, String agentId, String prompt) {
        MirroringEvents mirror = new MirroringEvents(parentEvents, agentId + "-");
        Outcome outcome = runEngine(buildChildSystemPrompt(profile), profile.tools(), runContext.mcpTools(),
                null, null, agentId, prompt, mirror);
        return new Outcome(outcome.content(), mirror.toolUseCount.get(),
                outcome.inputTokens(), outcome.outputTokens(), outcome.failed());
    }

    private Outcome runEngine(String systemPrompt, List<String> allowedTools, List<ToolCallback> mcpTools,
                              String knowledgeContext, String skillsContext, String agentId, String prompt,
                              AgentEvents childEvents) {
        ConversationState childState = new ConversationState();
        // 子代理/群成员注册表不含 render_ui：不注入结构化 UI 决策段，避免诱导无效工具调用
        ContextBuilder childContext = new ContextBuilder(
                systemPrompt, knowledgeContext, skillsContext, runContext.fs(), modelName,
                null, false);
        AgentEngine child = new AgentEngine(chatModel, config, reasoningEffort, childContext,
                childState, runContext.chatId() + "/" + agentId, childEvents, runContext.tasks());
        ToolRegistry childRegistry = toolsetFactory.build(runContext.chatId(), childState,
                mcpTools, allowedTools, null, null, runContext.hooks());
        child.bindTools(childRegistry);
        try {
            child.runTurn(prompt, true);
            return new Outcome(finalText(childState), 0,
                    child.inputTokens(), child.outputTokens(), false);
        } catch (Exception e) {
            log.warn("子代理执行失败 ({}): {}", agentId, e.getMessage());
            // 失败前已消耗的 token 仍要计入（子代理可能跑了多步才失败）
            return new Outcome("(Subagent failed: " + e.getMessage() + ")", 0,
                    child.inputTokens(), child.outputTokens(), true);
        } finally {
            // registry 可能因装配期异常为 null（此时 bindTools 已抛，走不到这里，
            // 但保持与未来改动兼容）：不能让它把原始异常顶替成 NPE
            if (childRegistry != null) {
                childRegistry.shutdown();
            }
        }
    }

    /** 群成员事件桥：回答增量外流给展示回调，其余（思考/工具/清单）不打扰主会话 */
    private static final class MemberStreamingEvents implements AgentEvents {
        private final MemberPresenter presenter;
        private final String agentId;
        private final java.util.concurrent.atomic.AtomicInteger toolUseCount =
                new java.util.concurrent.atomic.AtomicInteger();

        private MemberStreamingEvents(MemberPresenter presenter, String agentId) {
            this.presenter = presenter;
            this.agentId = agentId;
        }

        @Override
        public void thinkingDelta(String delta) {
        }

        @Override
        public void answerDelta(String delta) {
            if (presenter != null) {
                presenter.delta(agentId, delta);
            }
        }

        @Override
        public void toolUse(String id, String name) {
        }

        @Override
        public void toolArgs(String id, String delta) {
        }

        @Override
        public void toolResult(String id, String name, String content, boolean error, long durationMs) {
            toolUseCount.incrementAndGet();
        }

        @Override
        public void todosChanged() {
        }

        @Override
        public void retryScheduled(int attempt, int maxAttempts, String reason) {
        }

        @Override
        public void stepFinished(int step, long inputTokens, long outputTokens, long cachedInputTokens,
                                 boolean cachedInputKnown,
                                 List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
        }

        @Override
        public void compacted(String summary) {
        }
    }

    private String buildChildSystemPrompt(AgentProfiles.Profile profile) {
        return profile.systemPrompt() + "\n\n" + profile.description();
    }

    /** 子代理最终文本（最后一条助手消息） */
    private String finalText(ConversationState state) {
        for (int i = state.entries().size() - 1; i >= 0; i--) {
            var entry = state.entries().get(i);
            if (entry.kind == com.mio.ai.framework.zagent.history.ConversationEntry.Kind.ASSISTANT
                    && entry.text != null && !entry.text.isBlank()) {
                return entry.text;
            }
        }
        return "(Subagent completed but returned no output.)";
    }

    /** 父级事件镜像：工具调用可见，文本/思考不外流（zcode emitParentEvent 语义裁剪） */
    private static final class MirroringEvents implements AgentEvents {
        private final AgentEvents parent;
        private final String idPrefix;
        private final AtomicInteger toolUseCount = new AtomicInteger();

        private MirroringEvents(AgentEvents parent, String idPrefix) {
            this.parent = parent;
            this.idPrefix = idPrefix;
        }

        @Override
        public void thinkingDelta(String delta) {
            // 子代理思考不外流
        }

        @Override
        public void answerDelta(String delta) {
            // 子代理正文作为工具结果返回，不直接外流
        }

        @Override
        public void toolUse(String id, String name) {
            parent.toolUse(idPrefix + id, name);
        }

        @Override
        public void toolArgs(String id, String delta) {
            parent.toolArgs(idPrefix + id, delta);
        }

        @Override
        public void toolResult(String id, String name, String content, boolean error, long durationMs) {
            toolUseCount.incrementAndGet();
            parent.toolResult(idPrefix + id, name, content, error, durationMs);
        }

        @Override
        public void todosChanged() {
            // 子代理清单不推送父级计划面板
        }

        @Override
        public void retryScheduled(int attempt, int maxAttempts, String reason) {
            parent.retryScheduled(attempt, maxAttempts, reason);
        }

        @Override
        public void stepFinished(int step, long inputTokens, long outputTokens, long cachedInputTokens,
                                 boolean cachedInputKnown,
                                 List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
            // 子代理用量并入 usage 块汇报，不逐步上抛
        }

        @Override
        public void compacted(String summary) {
            // 子代理压缩不打扰父级
        }
    }
}
