package com.mio.ai.admin.model.vo;

import com.mio.ai.resource.model.vo.log.TokenTrendVO;
import com.mio.ai.resource.model.vo.log.UsageStatsVO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 管理后台总览图表数据：全站口径（不分用户）
 */
@Data
public class AdminOverviewChartsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 统计天数（7/30） */
    private Integer days;

    /** 每日趋势：轮次 / Token / 失败轮次 */
    private List<DayTrend> trend;

    /** 模型 Token 日趋势（Top 6 + 其他，复用用户侧洞察的序列结构） */
    private List<TokenTrendVO.Series> modelTrend;

    /** 智能体调用次数分布（value=调用次数，排除群聊本体） */
    private List<UsageStatsVO.NameValue> agentDistribution;

    /** 用户 Token 排行（value=Token 总量，来源 user_stats_daily） */
    private List<UsageStatsVO.NameValue> userRank;

    @Data
    public static class DayTrend implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** yyyy-MM-dd */
        private String date;

        private long turns;

        private long tokens;

        private long failedTurns;
    }
}
