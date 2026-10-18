package com.mio.ai.resource.service.log.impl;

/**
 * 四类使用日志归一后的分析行：flow/drill/散点共用同一份口径。
 *
 * @param weight          流向/下钻权重（agent 为 Token，其余为调用次数 1）
 * @param measure         散点纵轴值（Token / Chunk / 耗时 / 次数）
 * @param time            事件时间（epoch millis）
 * @param status          1-成功 0-失败
 * @param conversationId  会话标识（用于日明细去重）
 */
record UsageRow(
        Long userId,
        Long agentId,
        String agentName,
        String model,
        String targetName,
        long weight,
        double measure,
        long time,
        int status,
        String conversationId
) {
}
