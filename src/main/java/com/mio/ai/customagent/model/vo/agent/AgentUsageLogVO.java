package com.mio.ai.customagent.model.vo.agent;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志视图对象
 */
@Data
public class AgentUsageLogVO implements Serializable {

    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 输入Token数
     */
    private Integer inputTokens;

    /**
     * 输出Token数
     */
    private Integer outputTokens;

    /**
     * 总Token数
     */
    private Integer totalTokens;

    /**
     * 费用
     */
    private BigDecimal cost;

    /**
     * 响应时间（毫秒）
     */
    private Integer responseTime;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private Date createTime;
}
