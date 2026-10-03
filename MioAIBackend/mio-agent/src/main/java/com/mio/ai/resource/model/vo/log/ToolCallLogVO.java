package com.mio.ai.resource.model.vo.log;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志视图对象
 */
@Data
public class ToolCallLogVO implements Serializable {

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
     * 状态
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

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
    private Date createTime;
}
