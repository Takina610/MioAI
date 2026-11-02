package com.mio.ai.framework.zagent.subagent;

/**
 * 子代理启动器（zcode SubagentPort 的对位移植）：Agent 工具经此派生子运行时。
 */
public interface SubagentLauncher {

    /**
     * @return 运行结果；后台模式下 content 为启动说明
     */
    Result launch(String subagentType, String description, String prompt, boolean background);

    /**
     * 一次委派的结果。
     *
     * @param failed 是否以失败收尾（子代理异常/装配失败/超时）：上层据此给模型结构化失败标记，
     *               让它决定重试、换人或自行完成，而不是把报错文本当成正常输出复述
     */
    record Result(String agentId, String type, String prompt, String content,
                  int toolUseCount, long tokens, long durationMs, String backgroundTaskId,
                  boolean failed) {

        public Result(String agentId, String type, String prompt, String content,
                      int toolUseCount, long tokens, long durationMs, String backgroundTaskId) {
            this(agentId, type, prompt, content, toolUseCount, tokens, durationMs,
                    backgroundTaskId, false);
        }

        public boolean launchedInBackground() {
            return backgroundTaskId != null;
        }
    }
}
