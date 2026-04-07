package com.mio.ai.customagent.model.dto.mcptool;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具更新请求
 */
@Data
public class McpToolUpdateRequest implements Serializable {

    /**
     * 工具ID
     */
    private Long id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 配置（JSON格式）
     */
    private String config;

    /**
     * 工具信息：工具列表（JSON格式）
     */
    private String toolInfo;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否公开
     */
    private Integer isPublic;
}
