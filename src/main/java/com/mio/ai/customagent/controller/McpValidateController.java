package com.mio.ai.customagent.controller;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.vo.McpValidateResultVO;
import com.mio.ai.customagent.service.McpValidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: Takina
 * @date: 2026/4/7 14:28
 * @description:
 */

@RequestMapping("/mcp-tools")
@RestController
public class McpValidateController {

    @Autowired
    McpValidateService mcpValidateService;

    @PostMapping("/validate")
    public BaseResponse<McpValidateResultVO> validateMcpConfig(@RequestBody McpValidateRequest request) {
        McpValidateResultVO result = mcpValidateService.validateMcpConfig(request);
        return ResultUtils.success(result);
    }
}
