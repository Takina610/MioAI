package com.mio.ai.resource.controller.agent;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.agentskill.AgentSkillAddRequest;
import com.mio.ai.resource.model.entity.AgentSkill;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.security.AccessGuardService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 智能体-技能关联接口
 */
@Slf4j
@RestController
@RequestMapping("/agent-skill")
public class AgentSkillController {

    @Resource
    private AgentSkillService agentSkillService;

    @Resource
    private AccessGuardService accessGuardService;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 绑定技能：需为智能体所有者，且对该技能有读权限（自有或公开）
     */
    @PostMapping
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Long> addAgentSkill(@RequestBody AgentSkillAddRequest request,
                                            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(request.getAgentId(), userId);
        accessGuardService.checkSkillReadable(request.getSkillId(), userId);
        AgentSkill binding = new AgentSkill();
        binding.setAgentId(request.getAgentId());
        binding.setSkillId(request.getSkillId());
        binding.setEnabled(request.getEnabled());
        return ResultUtils.success(agentSkillService.addAgentSkill(binding));
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteAgentSkill(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        AgentSkill binding = agentSkillService.getById(id);
        if (binding == null) {
            return ResultUtils.success(false);
        }
        accessGuardService.checkAgentOwner(binding.getAgentId(), userId);
        return ResultUtils.success(agentSkillService.deleteAgentSkill(id));
    }

    @DeleteMapping("/agent/{agentId}/skill/{skillId}")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> deleteByAgentIdAndSkillId(@PathVariable Long agentId, @PathVariable Long skillId,
                                                           HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        return ResultUtils.success(agentSkillService.deleteByAgentIdAndSkillId(agentId, skillId));
    }

    /**
     * 智能体绑定的技能ID列表：仅所有者可查
     */
    @GetMapping("/agent/{agentId}")
    public BaseResponse<List<Long>> getSkillIdsByAgentId(@PathVariable Long agentId,
                                                         HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        return ResultUtils.success(agentSkillService.getSkillIdsByAgentId(agentId));
    }
}
