package com.mio.ai.framework.zagent;

/**
 * 引擎运行配置（对应 mio.ai.agent.* 与压缩窗口参数）。
 */
public record AgentEngineConfig(
        int maxSteps,
        long streamTimeoutSeconds,
        int modelRetries,
        long contextWindowTokens) {

    public static AgentEngineConfig defaults() {
        return new AgentEngineConfig(100, 1800, 3, 200_000);
    }

    public AgentEngineConfig {
        maxSteps = maxSteps > 0 ? maxSteps : 100;
        streamTimeoutSeconds = streamTimeoutSeconds > 0 ? streamTimeoutSeconds : 1800;
        modelRetries = Math.max(0, modelRetries);
        contextWindowTokens = contextWindowTokens > 0 ? contextWindowTokens : 200_000;
    }
}
