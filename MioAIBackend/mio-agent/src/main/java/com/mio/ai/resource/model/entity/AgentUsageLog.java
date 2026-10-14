package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志实体类
 */
@Data
@TableName("agent_usage_log")
public class AgentUsageLog implements Serializable {

    @TableId(type = IdType.AUTO)
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
     * 模型名（本轮会话使用的模型标识；存量行为 NULL，统计时归入"未知模型"）
     */
    private String model;

    /**
     * 输入Token数
     */
    private Integer inputTokens;

    /**
     * 输出Token数
     */
    private Integer outputTokens;

    /**
     * 缓存读取Token数（上游提示词缓存命中；网关无明细时为 NULL）
     */
    private Integer cachedTokens;

    /**
     * 费用
     */
    private BigDecimal cost;

    /**
     * 响应时间（毫秒）
     */
    private Integer responseTime;

    /**
     * 状态（1-成功 0-失败）
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
