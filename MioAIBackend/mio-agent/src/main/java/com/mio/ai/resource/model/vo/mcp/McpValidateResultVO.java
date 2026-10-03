package com.mio.ai.resource.model.vo.mcp;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/7
 * @description: MCP校验结果
 */
@Data
public class McpValidateResultVO implements Serializable {

    /**
     * 是否校验成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 错误类型：CONFIG_INVALID, CONNECTION_FAILED, AUTH_FAILED, TIMEOUT, UNKNOWN
     */
    private String errorType;

    /**
     * 非致命提示（如配置包含多个服务器节点时只校验第一个）
     */
    private List<String> warnings;

    /**
     * 工具列表
     */
    private List<McpToolInfo> tools;

    /**
     * 服务器信息
     */
    private ServerInfo serverInfo;

    /**
     * MCP工具信息
     */
    @Data
    public static class McpToolInfo implements Serializable {
        /**
         * 工具名称
         */
        private String name;

        /**
         * 工具描述
         */
        private String description;

        /**
         * 输入参数Schema
         */
        private String inputSchema;
    }

    /**
     * MCP服务器信息
     */
    @Data
    public static class ServerInfo implements Serializable {
        /**
         * 服务器名称
         */
        private String name;

        /**
         * 服务器版本
         */
        private String version;

        /**
         * 协议版本
         */
        private String protocolVersion;
    }
}
