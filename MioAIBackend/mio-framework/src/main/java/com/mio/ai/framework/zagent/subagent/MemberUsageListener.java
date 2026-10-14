package com.mio.ai.framework.zagent.subagent;

/**
 * 群成员用量回调：一次成员委派（含后台任务）结束后触发，产品侧据此把成员 token
 * 按成员自身身份落库——群聊统计里"实际调用的智能体"由此可分。
 */
public interface MemberUsageListener {

    /**
     * @param memberAgentId 成员智能体ID
     * @param inputTokens   本次委派输入Token
     * @param outputTokens  本次委派输出Token
     * @param durationMs    本次委派耗时（毫秒）
     * @param failed        是否失败（装配失败/执行异常）
     */
    void onUsage(long memberAgentId, long inputTokens, long outputTokens, long durationMs, boolean failed);
}
