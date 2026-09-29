package com.mio.ai.customagent.controller.agent;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.agentknowledge.AgentKnowledgeAddRequest;
import com.mio.ai.customagent.model.entity.AgentKnowledge;
import com.mio.ai.customagent.service.agent.AgentKnowledgeService;
import com.mio.ai.customagent.service.security.AccessGuardService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联接口
 */
@Slf4j
@RestController
@RequestMapping("/agent-knowledge")
public class AgentKnowledgeController {

    @Resource
    private AgentKnowledgeService agentKnowledgeService;

    @Resource
    private AccessGuardService accessGuardService;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 绑定知识库：需为智能体所有者，且对该知识库有读权限（自有或公开）
     */
    @PostMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Long> addAgentKnowledge(@RequestBody AgentKnowledgeAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(request.getAgentId(), userId);
        accessGuardService.checkKbReadable(request.getKbId(), userId);
        Long id = agentKnowledgeService.addAgentKnowledge(request);
        return ResultUtils.success(id);
    }

    /**
     * 解绑：校验关联关系存在且智能体属于当前用户
     */
    @DeleteMapping("/{id}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteAgentKnowledge(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        AgentKnowledge binding = agentKnowledgeService.getById(id);
        if (binding == null) {
            return ResultUtils.success(false);
        }
        accessGuardService.checkAgentOwner(binding.getAgentId(), userId);
        boolean result = agentKnowledgeService.deleteAgentKnowledge(id);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/agent/{agentId}/kb/{kbId}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteByAgentIdAndKbId(@PathVariable Long agentId, @PathVariable Long kbId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        boolean result = agentKnowledgeService.deleteByAgentIdAndKbId(agentId, kbId);
        return ResultUtils.success(result);
    }

    /**
     * 查询智能体绑定的知识库ID列表：仅所有者可查
     */
    @GetMapping("/agent/{agentId}")
    public BaseResponse<List<Long>> getKbIdsByAgentId(@PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        List<Long> kbIds = agentKnowledgeService.getKbIdsByAgentId(agentId);
        return ResultUtils.success(kbIds);
    }
}
