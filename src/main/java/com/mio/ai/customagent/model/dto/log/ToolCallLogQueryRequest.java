package com.mio.ai.customagent.model.dto.log;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolCallLogQueryRequest extends PageRequest implements Serializable {

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
     * 状态
     */
    private Integer status;
}
