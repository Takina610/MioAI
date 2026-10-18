package com.mio.ai.resource.model.vo.log;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 使用记录深度分析视图：使用去向（桑基）/ 使用构成（矩形树图）/ 使用明细（散点）
 */
@Data
public class UsageAnalyticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计类型：agent / knowledge / mcp / skill */
    private String type;

    /** 流向与下钻的权重口径（如 Token 数、检索次数） */
    private String valueLabel;
    private String valueUnit;

    /** 散点图纵轴口径（如 Token 数、Chunk 数、执行耗时） */
    private String measureLabel;
    private String measureUnit;

    private Flow flow;
    private List<DrillNode> drill;

    /** 散点事件（超量时等距抽样） */
    private List<EventPoint> events;
    /** 抽样前的事件总数 */
    private long totalEvents;

    @Data
    public static class Flow implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<FlowNode> nodes;
        private List<FlowLink> links;
    }

    @Data
    public static class FlowNode implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 桑基图内部唯一键（不同层级可能重名） */
        private String name;
        /** 节点展示名 */
        private String displayName;
        /** 层级：0 用户 / 1 智能体 / 2 目标（无用户层时整体前移） */
        private int layer;
        private long value;
    }

    @Data
    public static class FlowLink implements Serializable {
        private static final long serialVersionUID = 1L;
        private String source;
        private String target;
        private long value;
    }

    @Data
    public static class DrillNode implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String name;
        private long value;
        private List<DrillNode> children;
    }

    @Data
    public static class EventPoint implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 事件时间（epoch millis） */
        private long time;
        private double value;
        /** 1-成功 0-失败 */
        private int status;
        private String agentName;
        private String model;
        private String targetName;
        private String userName;
    }
}
