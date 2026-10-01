package com.mio.ai.customagent.model.vo.log;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 使用记录统计视图对象
 */
@Data
public class UsageStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 核心指标
     */
    private Map<String, MetricValue> metrics;

    /**
     * 时间序列数据（默认指标）
     */
    private List<TimePoint> trend;

    /**
     * 各指标时间序列数据，key: count/agentCount/tokens/avgTokens
     */
    private Map<String, List<TimePoint>> trendMap;

    /**
     * 上一周期各指标时间序列数据，key: count/agentCount/tokens/avgTokens
     */
    private Map<String, List<TimePoint>> prevTrendMap;

    /**
     * 分布数据（饼图）
     */
    private List<NameValue> distribution;

    /**
     * 排行榜数据
     */
    private List<NameValue> rank;

    @Data
    public static class MetricValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private Number value;
        private String unit;
        private Number trend;
    }

    @Data
    public static class TimePoint implements Serializable {
        private static final long serialVersionUID = 1L;
        private String time;
        private String fullTime;
        private Number value;
    }

    @Data
    public static class NameValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String name;
        private String avatar;
        private Number value;
    }
}
