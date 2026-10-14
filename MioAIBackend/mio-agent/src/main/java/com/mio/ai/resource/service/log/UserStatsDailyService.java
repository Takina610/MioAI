package com.mio.ai.resource.service.log;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.entity.UserStatsDaily;

import java.util.List;

/**
 * 用户每日用量累计服务接口：贡献图 / 连续天数 / 单日峰值 / 聊天时长的数据来源
 */
public interface UserStatsDailyService extends IService<UserStatsDaily> {

    /**
     * 当日累计原子累加（游客/异常静默忽略，绝不影响主流程）
     *
     * @param userId       用户ID（null 直接跳过）
     * @param inputTokens  本批输入Token增量
     * @param outputTokens 本批输出Token增量
     * @param calls        轮次增量
     * @param durationMs   聊天耗时增量（毫秒）
     */
    void accumulate(Long userId, long inputTokens, long outputTokens, int calls, long durationMs);

    /**
     * 用户全部每日累计（按日期升序）：连续天数/累计视图在此之上计算
     */
    List<UserStatsDaily> listByUser(Long userId);
}
