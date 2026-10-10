package com.mio.ai.framework.mcp;

/**
 * STDIO 命令无法启动（可执行文件不存在等）：与协议/连接错误区分开，
 * 校验层据此给出 PROCESS_START_FAILED 分类，避免落进 UNKNOWN
 */
public class McpProcessStartException extends RuntimeException {

    public McpProcessStartException(String message) {
        super(message);
    }
}
