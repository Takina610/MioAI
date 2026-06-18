package com.mio.ai.customagent.service.log;

import com.mio.ai.customagent.model.vo.log.UsageStatsVO;

import java.time.LocalDateTime;

/**
 * 使用记录统计服务接口
 */
public interface UsageStatsService {

    /**
     * 构建使用记录统计数据
     *
     * @param type      统计类型：agent / knowledge / mcp
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param userId    用户ID，为 null 时查询全部用户
     * @return 统计数据
     */
    UsageStatsVO buildStats(String type, LocalDateTime startTime, LocalDateTime endTime, Long userId);
}
