package com.mio.ai.framework.zagent.tools.delegation;

import com.mio.ai.framework.zagent.subagent.SubagentLauncher;

import static com.mio.ai.framework.zagent.tools.support.ToolDescriptions.AGENT_HEADER;
import com.mio.ai.framework.zagent.tools.contract.ToolContext;
import com.mio.ai.framework.zagent.tools.contract.ToolEntry;
import com.mio.ai.framework.zagent.tools.contract.ToolRegistry;
import com.mio.ai.framework.zagent.tools.contract.ToolUseFailure;
import com.mio.ai.framework.zagent.tools.support.Args;

/**
 * Agent 工具（zcode handlers/agent.ts 的对位移植）：
 * 前台阻塞运行子代理（最终文本 + agentId + usage 块返回）；后台入注册表。
 * <p>委派有墙钟预算：子代理内部逐步有 idle 超时，但整轮多步串行时总时长无界，
 * 前台委派会把父轮次线程一起挂住。预算耗尽由 {@link ToolRegistry} 的 future 超时
 * 转成结构化失败回给模型（可换人/降级），本轮其余工作照常继续。
 */
public final class AgentTool {

    private final SubagentLauncher launcher;
    private final long delegationTimeoutMs;
    /**
     * 单轮并行委派配额：AgentTool 每次 build() 新建（一次运行一个），
     * 因此信号量天然就是"本轮"范围。模型一轮里发起的委派超过配额时快速失败，
     * 而不是全部压进委派池排队——否则用户会看到"点了没反应"的长时间等待。
     */
    /**
     * 单轮并行委派配额：AgentTool 每次 build() 新建（一次运行一个），
     * 因此信号量天然就是"本轮"范围。模型一轮里发起的委派超过配额时快速失败，
     * 而不是全部压进委派池排队——否则用户会看到"点了没反应"的长时间等待。
     */
    private final java.util.concurrent.Semaphore delegationSlots;
    /** 单轮委派配额总数（仅用于失败文案） */
    private final int delegationQuota;
    /**
     * 配额获取的等待窗口：委派超时由 ToolRegistry 的 future.cancel(true) 触发，
     * 中断是异步投递的——执行线程收到中断后才走到 finally 归还配额。模型在超时后
     * 立刻重试时可能撞上这段窗口，短暂等待即可，不该误报"并发已满"。
     * 不能给太长：模型正在等这个调用返回，长等待会让单步请求看起来卡死。
     */
    private static final long PERMIT_WAIT_MS = 300;

    public AgentTool(SubagentLauncher launcher) {
        this(launcher, 0, 0);
    }

    /**
     * @param delegationTimeoutMs 单次委派的墙钟预算；&lt;=0 表示不限时（保留旧行为出口）
     * @param maxConcurrent       单轮并行委派上限；&lt;=0 表示不限
     */
    public AgentTool(SubagentLauncher launcher, long delegationTimeoutMs, int maxConcurrent) {
        this.launcher = launcher;
        this.delegationTimeoutMs = Math.max(0, delegationTimeoutMs);
        this.delegationQuota = Math.max(0, maxConcurrent);
        this.delegationSlots = maxConcurrent > 0
                ? new java.util.concurrent.Semaphore(maxConcurrent) : null;
    }

    public ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "description":{"type":"string","description":"A short (3-5 word) description of the task"},
                "prompt":{"type":"string","description":"The task for the agent to perform"},
                "subagent_type":{"type":"string","description":"The type of specialized agent to use for this task"},
                "run_in_background":{"type":"boolean","description":"Set to true to run this agent in the background."}},
                "required":["description","prompt"]}""";
        // 并发编排工具：多个委派相互独立，可并行执行（群聊并行委派多个成员不叠加等待）
        return ToolEntry.ofDelegation("Agent", AGENT_HEADER, schema, delegationTimeoutMs, this::execute);
    }

    public String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        if (ctx.subagents == null) {
            throw new ToolUseFailure(30, "Subagents are not available in this context.");
        }
        String description = Args.str(input, "description");
        String prompt = Args.str(input, "prompt");
        String type = Args.str(input, "subagent_type");
        boolean background = Args.bool(input, "run_in_background");
        if (prompt == null) {
            throw new ToolUseFailure(31, "prompt is required.");
        }
        if (delegationSlots != null && !acquireSlot()) {
            throw new ToolUseFailure(33, "Too many agents are already running in this turn (limit "
                    + delegationQuota + "). Wait for the running delegations to return before "
                    + "starting more, or handle part of the task yourself.");
        }
        try {
            return launch(type, description, prompt, background, ctx);
        } finally {
            // 前台委派在返回时真正结束，配额即可归还；后台委派的资源边界由
            // DelegationExecutors.background() 的有界池负责，这里不重复占用本轮配额，
            // 否则一个后台委派会让整轮剩下的前台委派全部被拒。
            if (delegationSlots != null) {
                delegationSlots.release();
            }
        }
    }

    /**
     * 取配额：先用极短窗口自然排队（覆盖"上一批委派刚好在收尾"的正常并发），
     * 窗口内拿不到才判定配额耗尽。等待期必须是毫秒级——模型在等这个调用返回，
     * 长等待会让单步模型请求看起来像卡死。
     */
    private boolean acquireSlot() {
        try {
            return delegationSlots.tryAcquire(PERMIT_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String launch(String type, String description, String prompt, boolean background,
                          ToolContext ctx) {
        SubagentLauncher.Result result;
        try {
            result = ctx.subagents.launch(type, description, prompt, background);
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            // 委派池满：明确告知模型稍后再试或自己完成，而不是让它以为委派已启动
            throw new ToolUseFailure(32, "Delegation capacity is exhausted (too many agents are "
                    + "running at once). Wait for running agents to finish, or complete the task "
                    + "yourself / delegate to fewer members.");
        }
        if (result.launchedInBackground()) {
            return "Agent " + result.agentId() + " (" + result.type() + ") launched in background.\n"
                    + "task_id: " + result.backgroundTaskId() + "\n"
                    + "Briefly tell the user what you launched, then end your response; "
                    + "do not duplicate this agent's work while it runs.";
        }
        // 失败带结构化标记：主控据此决定重试/换人/自行完成，而不是把报错当正文复述
        if (result.failed()) {
            return "<delegation_failed agent=\"" + result.type() + "\" agentId=\"" + result.agentId() + "\">\n"
                    + (result.content() == null || result.content().isBlank()
                    ? "(no output)" : result.content())
                    + "\n</delegation_failed>\n"
                    + "This member/agent could not complete the task. You may retry with a narrower "
                    + "prompt, delegate to a different member, or complete the work yourself.";
        }
        StringBuilder sb = new StringBuilder(result.content() == null || result.content().isBlank()
                ? "(Subagent completed but returned no output.)" : result.content());
        sb.append("\n\nagentId: ").append(result.agentId())
                .append(" (the agent's conversation is not resumable; launch a new Agent with full context "
                        + "if follow-up work is needed)");
        sb.append("\n<usage>subagent_tokens: ").append(result.tokens())
                .append("\ntool_uses: ").append(result.toolUseCount())
                .append("\nduration_ms: ").append(result.durationMs())
                .append("</usage>");
        return sb.toString();
    }
}
