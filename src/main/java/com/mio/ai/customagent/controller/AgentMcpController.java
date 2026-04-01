package com.mio.ai.customagent.controller;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.agentmcp.AgentMcpAddRequest;
import com.mio.ai.customagent.service.AgentMcpService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP关联接口
 */
@Slf4j
@RestController
@RequestMapping("/agent-mcp")
public class AgentMcpController {

    @Resource
    private AgentMcpService agentMcpService;

    @PostMapping
    public BaseResponse<Long> addAgentMcp(@RequestBody AgentMcpAddRequest request) {
        Long id = agentMcpService.addAgentMcp(request);
        return ResultUtils.success(id);
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteAgentMcp(@PathVariable Long id) {
        boolean result = agentMcpService.deleteAgentMcp(id);
        return ResultUtils.success(result);
    }

    @GetMapping("/agent/{agentId}")
    public BaseResponse<List<Long>> getMcpIdsByAgentId(@PathVariable Long agentId) {
        List<Long> mcpIds = agentMcpService.getMcpIdsByAgentId(agentId);
        return ResultUtils.success(mcpIds);
    }
}
