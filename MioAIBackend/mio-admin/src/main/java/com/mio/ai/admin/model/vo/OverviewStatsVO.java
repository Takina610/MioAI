package com.mio.ai.admin.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理后台总览统计
 */
@Data
public class OverviewStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long users;

    private long agents;

    private long knowledgeBases;

    private long mcpTools;

    private long skills;

    private long conversations;

    private long messages;

    private long chatFiles;

    /** 今日对话轮次（agent_usage_log 当日记录数） */
    private long todayTurns;

    /** 今日输入 Token */
    private long todayInputTokens;

    /** 今日输出 Token */
    private long todayOutputTokens;

    /** 今日失败轮次 */
    private long todayFailedTurns;

    /** 后台运行中任务数 */
    private long runningTasks;
}
