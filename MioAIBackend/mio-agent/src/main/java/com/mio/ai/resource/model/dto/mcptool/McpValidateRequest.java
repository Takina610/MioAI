package com.mio.ai.resource.model.dto.mcptool;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/7
 * @description: MCP校验请求
 */
@Data
public class McpValidateRequest implements Serializable {

    /**
     * 配置（JSON格式）
     */
    private String config;
}
