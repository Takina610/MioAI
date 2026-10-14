package com.mio.ai.resource.service.log;

import com.mio.ai.resource.model.vo.log.ContributionVO;
import com.mio.ai.resource.model.vo.log.TokenTrendVO;
import com.mio.ai.resource.model.vo.log.UserProfileVO;

/**
 * 用户数据洞察服务：数据画像 / 贡献图 / 每日 Token 趋势（含模型用量）
 */
public interface UserInsightService {

    /**
     * 个人数据画像：注册信息 + 全资源计数 + 最常用模型/智能体
     */
    UserProfileVO buildProfile(Long userId);

    /**
     * 贡献图：指定年份的逐日 Token 用量 + 全历史汇总指标（累计/峰值/最长聊天时长/连续天数）
     *
     * @param year 年份（null 取当前年份）
     */
    ContributionVO buildContribution(Long userId, Integer year);

    /**
     * 每日 Token 趋势：按模型或智能体拆分的多序列日趋势（趋势折线 + 模型用量饼图共用）
     *
     * @param days      最近 N 天（1-365，null 取 7）
     * @param dimension model-按模型拆分 / agent-按智能体拆分（默认 model）
     */
    TokenTrendVO buildTokenTrend(Long userId, Integer days, String dimension);
}
