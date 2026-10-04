package com.mio.ai.framework.zagent.subagent;

/**
 * 子代理启动器（zcode SubagentPort 的对位移植）：Agent 工具经此派生子运行时。
 */
public interface SubagentLauncher {

    /**
     * @return 运行结果；后台模式下 content 为启动说明
     */
    Result launch(String subagentType, String description, String prompt, boolean background);

    record Result(String agentId, String type, String prompt, String content,
                  int toolUseCount, long tokens, long durationMs, String backgroundTaskId) {

        public boolean launchedInBackground() {
            return backgroundTaskId != null;
        }
    }
}
