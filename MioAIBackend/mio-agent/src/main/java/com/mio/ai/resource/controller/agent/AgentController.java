package com.mio.ai.resource.controller.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.agent.AgentAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentQueryRequest;
import com.mio.ai.resource.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.resource.model.vo.agent.AgentDetailVO;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.resource.service.agent.AgentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/agents")
public class AgentController {

    @Resource
    private AgentService agentService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private com.mio.ai.resource.service.security.AccessGuardService accessGuardService;

    @PostMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Long> addAgent(@Valid @RequestBody AgentAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Long id = agentService.addAgent(request, userId);
        return ResultUtils.success(id);
    }

    @PutMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> updateAgent(@Valid @RequestBody AgentUpdateRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = agentService.updateAgent(request, userId);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/{id:\\d+}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteAgent(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = agentService.deleteAgent(id, userId);
        return ResultUtils.success(result);
    }

    @GetMapping("/market")
    @Cacheable(value = "agents")
    public BaseResponse<Page<AgentVO>> getMarketAgents(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "12") long size) {
        Page<AgentVO> page = agentService.getPublicAgents(current, size);
        return ResultUtils.success(page);
    }

    @GetMapping("/{id:\\d+}")
    @Cacheable(value = "agents", key = "#id")
    public BaseResponse<AgentVO> getAgent(@PathVariable Long id, HttpServletRequest httpRequest) {
        // 游客也可读取内置/公开已发布的智能体（chat 页未登录可见）
        Long userId = redisComponent.getUserIdNullable(httpRequest.getHeader("token"));
        accessGuardService.checkAgentReadable(id, userId);
        AgentVO agent = agentService.getAgentById(id);
        return ResultUtils.success(agent);
    }

    @GetMapping("/{id:\\d+}/detail")
    public BaseResponse<AgentDetailVO> getAgentDetail(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserIdNullable(httpRequest.getHeader("token"));
        accessGuardService.checkAgentReadable(id, userId);
        AgentDetailVO agent = agentService.getAgentDetailById(id, userId);
        return ResultUtils.success(agent);
    }

    @PostMapping("/list")
    public BaseResponse<Page<AgentVO>> listAgents(@RequestBody AgentQueryRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        request.setUserId(userId);
        Page<AgentVO> page = agentService.queryAgents(request);
        return ResultUtils.success(page);
    }

    @PostMapping("/{agentId:\\d+}/publish")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> publishAgent(
            @PathVariable Long agentId,
            @Valid @RequestBody AgentUpdateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        agentService.publishAgent(agentId, request, userId);
        return ResultUtils.success(true);
    }
}
