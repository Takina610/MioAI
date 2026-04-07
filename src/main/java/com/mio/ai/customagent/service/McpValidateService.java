package com.mio.ai.customagent.service;

import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.vo.McpValidateResultVO;

/**
 * @author: Takina
 * @date: 2026/4/7 14:29
 * @description:
 */

public interface McpValidateService {

    /**
     * 校验MCP配置
     */
    McpValidateResultVO validateMcpConfig(McpValidateRequest request);
}
