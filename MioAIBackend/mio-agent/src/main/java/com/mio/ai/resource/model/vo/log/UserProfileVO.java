package com.mio.ai.resource.model.vo.log;

import lombok.Data;

import java.util.Date;

/**
 * 用户数据画像 VO：跨智能体/知识库/MCP/技能的聚合画像（使用记录页"数据画像"区）
 */
@Data
public class UserProfileVO {

    /**
     * 注册时间
     */
    private Date registerTime;

    /**
     * 注册天数
     */
    private Long registerDays;

    /**
     * 活跃天数（有用量记录的天数）
     */
    private Long activeDays;

    /**
     * 累计对话轮次
     */
    private Long totalTurns;

    /**
     * 累计会话数
     */
    private Long totalConversations;

    /**
     * 累计消息数（用户+智能体）
     */
    private Long totalMessages;

    /**
     * 累计 Token 数
     */
    private Long totalTokens;

    /**
     * 日均 Token（活跃日内平均）
     */
    private Long avgDailyTokens;

    /**
     * 最常用模型
     */
    private String favoriteModel;

    /**
     * 最常用模型的累计 Token
     */
    private Long favoriteModelTokens;

    /**
     * 最常用智能体ID
     */
    private Long favoriteAgentId;

    /**
     * 最常用智能体名称
     */
    private String favoriteAgentName;

    /**
     * 最常用智能体头像
     */
    private String favoriteAgentAvatar;

    /**
     * 最常用智能体的累计 Token
     */
    private Long favoriteAgentTokens;

    /**
     * 拥有智能体数
     */
    private Long agentCount;

    /**
     * 拥有知识库数
     */
    private Long knowledgeCount;

    /**
     * 拥有 MCP 数
     */
    private Long mcpCount;

    /**
     * 拥有技能数
     */
    private Long skillCount;

    /**
     * 累计工具调用次数
     */
    private Long toolCallCount;

    /**
     * 累计知识检索次数
     */
    private Long retrievalCount;

    /**
     * 累计技能使用次数
     */
    private Long skillUseCount;

    /**
     * 累计执行日志条数
     */
    private Long activityCount;
}
