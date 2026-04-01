package com.mio.ai.customagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志实体类
 */
@Data
@TableName("tool_call_log")
public class ToolCallLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 工具ID
     */
    private Long toolId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 输入参数（JSON格式）
     */
    private String inputParams;

    /**
     * 输出结果
     */
    private String outputResult;

    /**
     * 状态（1-成功 0-失败 2-超时）
     */
    private Integer status;

    /**
     * 执行时间（毫秒）
     */
    private Integer executionTime;

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
