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
import com.mio.ai.resource.model.dto.agent.AgentUserConfigRequest;
import com.mio.ai.resource.model.vo.agent.AgentDetailVO;
import com.mio.ai.resource.model.vo.agent.AgentUserConfigVO;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.resource.model.vo.agent.AgentVersionVO;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.resource.service.agent.AgentUserConfigService;
import com.mio.ai.resource.service.security.AccessGuardService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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
    private AgentUserConfigService agentUserConfigService;

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
    public BaseResponse<Page<AgentVO>> getMarketAgents(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "12") long size,
            HttpServletRequest httpRequest) {
        // MioBot 槽位按当前用户装配，结果因人而异，不能走全局缓存
        Long userId = redisComponent.getUserIdNullable(httpRequest.getHeader("token"));
        Page<AgentVO> page = agentService.getPublicAgents(current, size, userId);
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
    public BaseResponse<String> publishAgent(
            @PathVariable Long agentId,
            @Valid @RequestBody AgentUpdateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        String version = agentService.publishAgent(agentId, request, userId);
        return ResultUtils.success(version);
    }

    // ---------- 版本历史与回滚 ----------

    @GetMapping("/{agentId:\\d+}/versions")
    public BaseResponse<List<AgentVersionVO>> listVersions(
            @PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(agentService.listVersions(agentId, userId));
    }

    @PostMapping("/{agentId:\\d+}/versions/{versionId:\\d+}/rollback")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<String> rollbackVersion(
            @PathVariable Long agentId, @PathVariable Long versionId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        String version = agentService.rollbackToVersion(agentId, versionId, userId);
        return ResultUtils.success(version);
    }

    // ---------- 广场模板复制 ----------

    @PostMapping("/{templateId:\\d+}/fork")
    public BaseResponse<Long> forkAgent(
            @PathVariable Long templateId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        // 模板须当前用户可读（本人所有，或公开且已发布）
        accessGuardService.checkAgentReadable(templateId, userId);
        return ResultUtils.success(agentService.forkAgent(templateId, userId));
    }

    // ---------- 内置智能体的用户级个性化配置（MioBot 每人一套独立配置） ----------

    @GetMapping("/{agentId:\\d+}/my-config")
    public BaseResponse<AgentUserConfigVO> getMyConfig(
            @PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        requireBuiltinAgent(agentId);
        com.mio.ai.resource.model.entity.AgentUserConfig config =
                agentUserConfigService.getOverride(userId, agentId);
        if (config == null) {
            return ResultUtils.success(null);
        }
        AgentUserConfigVO vo = new AgentUserConfigVO();
        vo.setAgentId(config.getAgentId());
        vo.setSystemPrompt(config.getSystemPrompt());
        vo.setIcon(config.getIcon());
        vo.setIsPublic(config.getIsPublic());
        return ResultUtils.success(vo);
    }

    @PutMapping("/{agentId:\\d+}/my-config")
    public BaseResponse<AgentUserConfigVO> saveMyConfig(
            @PathVariable Long agentId,
            @Valid @RequestBody AgentUserConfigRequest request,
            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        requireBuiltinAgent(agentId);
        return ResultUtils.success(agentUserConfigService.saveConfig(agentId, request, userId));
    }

    @DeleteMapping("/{agentId:\\d+}/my-config")
    @CacheEvict(value = "agents", allEntries = true)
    public BaseResponse<Boolean> resetMyConfig(
            @PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        requireBuiltinAgent(agentId);
        return ResultUtils.success(agentUserConfigService.resetConfig(agentId, userId));
    }

    /** 个性化配置仅对内置智能体（user_id 为空，如 MioBot）开放 */
    private void requireBuiltinAgent(Long agentId) {
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (agent.getUserId() != null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅内置智能体支持个性化设置");
        }
    }
}
