package com.mio.ai.framework.zagent;

/**
 * 引擎运行配置（对应 mio.ai.agent.* 与压缩窗口参数）。
 */
public record AgentEngineConfig(
        int maxSteps,
        long streamTimeoutSeconds,
        int modelRetries,
        long contextWindowTokens,
        long delegationTimeoutMs,
        int maxConcurrentDelegations) {

    /** 委派总预算缺省值（10 分钟）：单次子代理/群成员运行的墙钟上限 */
    public static final long DEFAULT_DELEGATION_TIMEOUT_MS = 600_000;
    /** 单轮并行委派上限缺省值 */
    public static final int DEFAULT_MAX_CONCURRENT_DELEGATIONS = 4;

    public static AgentEngineConfig defaults() {
        return new AgentEngineConfig(100, 1800, 3, 200_000,
                DEFAULT_DELEGATION_TIMEOUT_MS, DEFAULT_MAX_CONCURRENT_DELEGATIONS);
    }

    /** 兼容既有四参调用（委派参数取缺省值：10 分钟预算、单轮 4 路并发） */
    public AgentEngineConfig(int maxSteps, long streamTimeoutSeconds, int modelRetries,
                             long contextWindowTokens) {
        this(maxSteps, streamTimeoutSeconds, modelRetries, contextWindowTokens,
                DEFAULT_DELEGATION_TIMEOUT_MS, DEFAULT_MAX_CONCURRENT_DELEGATIONS);
    }

    public AgentEngineConfig {
        maxSteps = maxSteps > 0 ? maxSteps : 100;
        streamTimeoutSeconds = streamTimeoutSeconds > 0 ? streamTimeoutSeconds : 1800;
        modelRetries = Math.max(0, modelRetries);
        contextWindowTokens = contextWindowTokens > 0 ? contextWindowTokens : 200_000;
        // 委派预算 <=0 视为不限制（保留显式关闭的出口）；并发上限同样 <=0 即不限
        delegationTimeoutMs = Math.max(0, delegationTimeoutMs);
        maxConcurrentDelegations = Math.max(0, maxConcurrentDelegations);
    }
}
