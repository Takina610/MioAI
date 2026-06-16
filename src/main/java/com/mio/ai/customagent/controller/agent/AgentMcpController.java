package com.mio.ai.customagent.controller.agent;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.agentmcp.AgentMcpAddRequest;
import com.mio.ai.customagent.service.agent.AgentMcpService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Long> addAgentMcp(@RequestBody AgentMcpAddRequest request) {
        Long id = agentMcpService.addAgentMcp(request);
        return ResultUtils.success(id);
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteAgentMcp(@PathVariable Long id) {
        boolean result = agentMcpService.deleteAgentMcp(id);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/agent/{agentId}/mcp/{mcpId}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteByAgentIdAndMcpId(@PathVariable Long agentId, @PathVariable Long mcpId) {
        boolean result = agentMcpService.deleteByAgentIdAndMcpId(agentId, mcpId);
        return ResultUtils.success(result);
    }

    @GetMapping("/agent/{agentId}")
    public BaseResponse<List<Long>> getMcpIdsByAgentId(@PathVariable Long agentId) {
        List<Long> mcpIds = agentMcpService.getMcpIdsByAgentId(agentId);
        return ResultUtils.success(mcpIds);
    }
}
