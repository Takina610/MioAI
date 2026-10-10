package com.mio.ai.resource.controller.mcp;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.resource.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.resource.service.mcp.McpValidateService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.servlet.http.HttpServletRequest;
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

    @Autowired
    RedisComponent redisComponent;

    /**
     * 校验会真实连接外部端点、STDIO 型还会在服务器上执行本地命令，必须登录后才能调用
     */
    @PostMapping("/validate")
    public BaseResponse<McpValidateResultVO> validateMcpConfig(@RequestBody McpValidateRequest request,
                                                               HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        McpValidateResultVO result = mcpValidateService.validateMcpConfig(request, userId);
        return ResultUtils.success(result);
    }
}
