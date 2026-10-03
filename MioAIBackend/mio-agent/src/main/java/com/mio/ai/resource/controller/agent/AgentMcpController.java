package com.mio.ai.resource.controller.agent;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.agentmcp.AgentMcpAddRequest;
import com.mio.ai.resource.model.entity.AgentMcp;
import com.mio.ai.resource.service.agent.AgentMcpService;
import com.mio.ai.resource.service.security.AccessGuardService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
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

    @Resource
    private AccessGuardService accessGuardService;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 绑定MCP工具：需为智能体所有者，且对该MCP工具有读权限（自有或公开）
     */
    @PostMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Long> addAgentMcp(@RequestBody AgentMcpAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(request.getAgentId(), userId);
        accessGuardService.checkMcpReadable(request.getMcpId(), userId);
        Long id = agentMcpService.addAgentMcp(request);
        return ResultUtils.success(id);
    }

    /**
     * 解绑：校验关联关系存在且智能体属于当前用户
     */
    @DeleteMapping("/{id}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteAgentMcp(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        AgentMcp binding = agentMcpService.getById(id);
        if (binding == null) {
            return ResultUtils.success(false);
        }
        accessGuardService.checkAgentOwner(binding.getAgentId(), userId);
        boolean result = agentMcpService.deleteAgentMcp(id);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/agent/{agentId}/mcp/{mcpId}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteByAgentIdAndMcpId(@PathVariable Long agentId, @PathVariable Long mcpId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        boolean result = agentMcpService.deleteByAgentIdAndMcpId(agentId, mcpId);
        return ResultUtils.success(result);
    }

    /**
     * 查询智能体绑定的MCP工具ID列表：仅所有者可查
     */
    @GetMapping("/agent/{agentId}")
    public BaseResponse<List<Long>> getMcpIdsByAgentId(@PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        List<Long> mcpIds = agentMcpService.getMcpIdsByAgentId(agentId);
        return ResultUtils.success(mcpIds);
    }
}
