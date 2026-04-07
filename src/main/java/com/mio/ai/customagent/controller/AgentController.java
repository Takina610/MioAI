package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.agent.AgentAddRequest;
import com.mio.ai.customagent.model.dto.agent.AgentQueryRequest;
import com.mio.ai.customagent.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.customagent.model.vo.AgentVO;
import com.mio.ai.customagent.service.AgentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体接口
 */
@Slf4j
@RestController
@RequestMapping("/agents")
public class AgentController {

    @Resource
    private AgentService agentService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Long> addAgent(@RequestBody AgentAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Long id = agentService.addAgent(request, userId);
        return ResultUtils.success(id);
    }

    @PutMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> updateAgent(@RequestBody AgentUpdateRequest request, HttpServletRequest httpRequest) {
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
    public BaseResponse<AgentVO> getAgent(@PathVariable Long id) {
        AgentVO agent = agentService.getAgentById(id);
        return ResultUtils.success(agent);
    }

    @PostMapping("/list")
    public BaseResponse<Page<AgentVO>> listAgents(@RequestBody AgentQueryRequest request) {
        Page<AgentVO> page = agentService.queryAgents(request);
        return ResultUtils.success(page);
    }
}
