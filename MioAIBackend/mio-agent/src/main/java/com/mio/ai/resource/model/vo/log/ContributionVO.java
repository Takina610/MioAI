package com.mio.ai.resource.model.vo.log;

import lombok.Data;

import java.util.List;

/**
 * 贡献图数据 VO：逐日 Token 用量 + 头部五项汇总指标。
 * 每日/每周/累计三种视图由前端基于 days 自行聚合。
 */
@Data
public class ContributionVO {

    /**
     * 逐日数据（date 升序，无数据日不补零）
     */
    private List<DayPoint> days;

    /**
     * 头部汇总指标（全历史口径，与所选年份无关）
     */
    private Summary summary;

    @Data
    public static class DayPoint {

        /**
         * 日期（yyyy-MM-dd）
         */
        private String date;

        /**
         * 当日 Token 总量（输入+输出）
         */
        private Long tokens;

        /**
         * 当日对话轮次
         */
        private Integer calls;
    }

    @Data
    public static class Summary {

        /**
         * 累计 Token 数（全历史）
         */
        private Long totalTokens;

        /**
         * 单日峰值 Token 数
         */
        private Long peakTokens;

        /**
         * 峰值所在日期（yyyy-MM-dd，无数据为 null）
         */
        private String peakDate;

        /**
         * 最长单日聊天时长（毫秒）
         */
        private Long longestChatMs;

        /**
         * 当前连续活跃天数（今天未活跃则从昨天回数）
         */
        private Integer currentStreak;

        /**
         * 最长连续活跃天数
         */
        private Integer longestStreak;
    }
}
