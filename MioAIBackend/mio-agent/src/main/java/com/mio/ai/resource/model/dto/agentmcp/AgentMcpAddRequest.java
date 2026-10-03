package com.mio.ai.resource.model.dto.agentmcp;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP关联创建请求
 */
@Data
public class AgentMcpAddRequest implements Serializable {

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * MCP工具ID
     */
    private Long mcpId;

    /**
     * 是否启用
     */
    private Integer enabled;

    /**
     * 配置覆盖（JSON格式）
     */
    private String configOverride;
}
