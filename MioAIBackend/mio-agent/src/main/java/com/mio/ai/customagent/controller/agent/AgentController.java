package com.mio.ai.customagent.controller.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.common.FileType;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.agent.AgentAddRequest;
import com.mio.ai.customagent.model.dto.agent.AgentQueryRequest;
import com.mio.ai.customagent.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.customagent.model.vo.agent.AgentDetailVO;
import com.mio.ai.customagent.model.vo.agent.AgentVO;
import com.mio.ai.customagent.service.agent.AgentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

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
    private R2Util r2Util;

    @Resource
    private com.mio.ai.customagent.service.security.AccessGuardService accessGuardService;

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
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        // 可见性校验：所有者/内置/公开已发布（在读取缓存前执行）
        accessGuardService.checkAgentUsable(id, userId);
        AgentVO agent = agentService.getAgentById(id);
        return ResultUtils.success(agent);
    }

    @GetMapping("/{id:\\d+}/detail")
    public BaseResponse<AgentDetailVO> getAgentDetail(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
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

    @PostMapping("/avatar/upload")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        
        try {
            String tempId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String avatarUrl = r2Util.uploadFile(file, FileType.AGENT_AVATAR, "temp_" + tempId);
            return ResultUtils.success(avatarUrl);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传头像失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/avatar/temp")
    public BaseResponse<Boolean> deleteTempAvatar(@RequestParam("url") String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像URL不能为空");
        }
        
        if (!avatarUrl.contains("temp_")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只能删除临时头像");
        }
        
        try {
            r2Util.deleteFile(avatarUrl);
            return ResultUtils.success(true);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除头像失败: " + e.getMessage());
        }
    }
}
