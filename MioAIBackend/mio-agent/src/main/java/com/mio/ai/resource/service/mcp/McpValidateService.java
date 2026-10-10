package com.mio.ai.resource.service.mcp;

import com.mio.ai.resource.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.resource.model.vo.mcp.McpValidateResultVO;

/**
 * @author: Takina
 * @date: 2026/4/7 14:29
 * @description:
 */

public interface McpValidateService {

    /**
     * 校验MCP配置（userId 用于 STDIO 门禁，可为 null）
     */
    McpValidateResultVO validateMcpConfig(McpValidateRequest request, Long userId);
}
