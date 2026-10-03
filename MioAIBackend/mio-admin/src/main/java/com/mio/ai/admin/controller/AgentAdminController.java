package com.mio.ai.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.admin.model.dto.AgentAdminUpdateRequest;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.agent.AgentQueryRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.enums.AgentStatusEnum;
import com.mio.ai.resource.model.enums.AgentTypeEnum;
import com.mio.ai.resource.model.vo.agent.AgentDetailVO;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.resource.service.agent.AgentService;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员智能体管理接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/admin/agents")
public class AgentAdminController {

    @Resource
    private AgentService agentService;

    /**
     * 分页查询智能体列表（管理员）
     */
    @PostMapping("/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<AgentVO>> listAgentByPage(@RequestBody AgentQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 50");

        // 管理员查询：包含所有类型（含内置），支持 name 和 status 筛选
        QueryWrapper<Agent> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(request.getName())) {
            queryWrapper.like("name", request.getName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }
        if (request.getType() != null) {
            queryWrapper.eq("type", request.getType());
        }
        queryWrapper.orderByDesc("update_time");

        Page<Agent> agentPage = agentService.page(new Page<>(current, size), queryWrapper);
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        List<AgentVO> voList = agentPage.getRecords().stream().map(agent -> {
            AgentVO vo = new AgentVO();
            BeanUtil.copyProperties(agent, vo);
            AgentTypeEnum typeEnum = AgentTypeEnum.getByCode(agent.getType());
            vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "未知");
            AgentStatusEnum statusEnum = AgentStatusEnum.getByCode(agent.getStatus());
            vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return vo;
        }).toList();
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    /**
     * 获取智能体详情（含关联的 MCP 和知识库）
     */
    @GetMapping("/{id}/detail")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<AgentDetailVO> getAgentDetail(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "智能体 id 不合法");
        AgentDetailVO detail = agentService.getAgentDetailById(id, null);
        return ResultUtils.success(detail);
    }

    /**
     * 更新智能体信息（管理员）
     * 状态规则：草稿(0)可变为已发布(1)或禁用(2)，已发布(1)和禁用(2)不可变为草稿(0)
     */
    @PutMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> updateAgent(@Valid @RequestBody AgentAdminUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        Agent agent = agentService.getById(request.getId());
        ThrowUtils.throwIf(agent == null, ErrorCode.NOT_FOUND_ERROR, "智能体不存在");

        // 状态变更校验
        if (request.getStatus() != null) {
            int oldStatus = agent.getStatus();
            int newStatus = request.getStatus();
            // 已发布或禁用 -> 草稿：不允许
            if ((oldStatus == 1 || oldStatus == 2) && newStatus == 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "已发布或已禁用的智能体不能变更为草稿状态");
            }
        }

        Agent updateAgent = new Agent();
        updateAgent.setId(request.getId());
        if (request.getName() != null) {
            updateAgent.setName(request.getName());
        }
        if (request.getDescription() != null) {
            updateAgent.setDescription(request.getDescription());
        }
        if (request.getSystemPrompt() != null) {
            updateAgent.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getStatus() != null) {
            updateAgent.setStatus(request.getStatus());
        }
        if (request.getIsPublic() != null) {
            updateAgent.setIsPublic(request.getIsPublic());
        }
        boolean result = agentService.updateById(updateAgent);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除智能体
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteAgent(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "智能体 id 不合法");
        boolean result = agentService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }
}
