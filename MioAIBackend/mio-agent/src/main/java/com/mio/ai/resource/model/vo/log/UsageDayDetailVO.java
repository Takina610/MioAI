package com.mio.ai.resource.model.vo.log;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 日历热力图下钻：某一天的使用明细
 */
@Data
public class UsageDayDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** yyyy-MM-dd */
    private String date;

    private long tokens;
    private long inputTokens;
    private long outputTokens;
    private long calls;
    private long conversations;
    private long chatDurationMs;

    private List<HourBucket> hours;
    private List<UsageStatsVO.NameValue> agents;
    private List<UsageStatsVO.NameValue> models;

    private long agentCalls;
    private long agentCount;
    private long knowledgeCalls;
    private long knowledgeCount;
    private long mcpCalls;
    private long mcpCount;
    private long skillCalls;
    private long skillCount;

    @Data
    public static class HourBucket implements Serializable {
        private static final long serialVersionUID = 1L;
        private int hour;
        /** 当前 tab 口径的量（agent=Token，其它=Chunk/耗时/次数） */
        private long measure;
        private long calls;
    }
}
