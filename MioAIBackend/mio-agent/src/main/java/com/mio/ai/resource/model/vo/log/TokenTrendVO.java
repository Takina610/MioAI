package com.mio.ai.resource.model.vo.log;

import lombok.Data;

import java.util.List;

/**
 * 每日 Token 趋势 VO：按模型或智能体拆分的多序列日趋势。
 * 饼图（模型/智能体用量占比）由前端基于 series 的 totalTokens/totalCalls 聚合。
 */
@Data
public class TokenTrendVO {

    /**
     * 拆分维度（model-模型 agent-智能体）
     */
    private String dimension;

    /**
     * 趋势序列（按总量降序，超出上限的并入"其他"）
     */
    private List<Series> series;

    @Data
    public static class Series {

        /**
         * 维度标识（模型名或智能体ID字符串，"其他"为 -1）
         */
        private String id;

        /**
         * 展示名（模型名 / 智能体真实名称）
         */
        private String name;

        /**
         * 智能体头像（模型维度为 null）
         */
        private String avatar;

        /**
         * 区间内 Token 总量
         */
        private Long totalTokens;

        /**
         * 区间内调用步数
         */
        private Long totalCalls;

        /**
         * 逐日 Token（date 升序，缺失日补零）
         */
        private List<Point> points;
    }

    @Data
    public static class Point {

        /**
         * 日期（yyyy-MM-dd）
         */
        private String date;

        /**
         * 当日 Token 总量
         */
        private Long tokens;
    }
}
