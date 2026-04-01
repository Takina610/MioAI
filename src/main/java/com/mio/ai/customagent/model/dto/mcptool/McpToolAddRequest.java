package com.mio.ai.customagent.model.dto.mcptool;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具创建请求
 */
@Data
public class McpToolAddRequest implements Serializable {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 配置（JSON格式）
     */
    private String config;

    /**
     * 是否公开
     */
    private Integer isPublic;
}
