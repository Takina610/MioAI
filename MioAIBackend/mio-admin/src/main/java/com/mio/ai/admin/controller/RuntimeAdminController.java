package com.mio.ai.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.admin.component.AdminNameResolver;
import com.mio.ai.admin.model.dto.AdminRecordQueryRequest;
import com.mio.ai.admin.model.vo.AdminRecordVO;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.mapper.task.AgentBackgroundTaskMapper;
import com.mio.ai.resource.model.entity.AgentBackgroundTask;
import com.mio.ai.resource.model.entity.AgentGroupMember;
import com.mio.ai.resource.model.entity.AgentUserConfig;
import com.mio.ai.resource.model.entity.AgentVersion;
import com.mio.ai.resource.service.agent.AgentGroupService;
import com.mio.ai.resource.service.agent.AgentUserConfigService;
import com.mio.ai.resource.service.agent.AgentVersionService;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员运行数据管理接口：群聊成员 / 智能体版本 / 用户个性化配置 / 后台任务
 */
@Slf4j
@RestController
@RequestMapping("/admin/runtime")
public class RuntimeAdminController {

    @Resource
    private AgentGroupService agentGroupService;

    @Resource
    private com.mio.ai.resource.service.agent.AgentService agentService;

    @Resource
    private AgentVersionService agentVersionService;

    @Resource
    private AgentUserConfigService agentUserConfigService;

    @Resource
    private AgentBackgroundTaskMapper backgroundTaskMapper;

    @Resource
    private AdminNameResolver nameResolver;

    // ---------- 群聊成员 ----------

    @PostMapping("/group-members/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchGroupMembers(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");
        QueryWrapper<AgentGroupMember> wrapper = new QueryWrapper<>();
        wrapper.eq(request.getGroupAgentId() != null, "group_agent_id", request.getGroupAgentId());
        wrapper.eq(request.getMemberAgentId() != null, "member_agent_id", request.getMemberAgentId());
        wrapper.orderByDesc("create_time");
        Page<AgentGroupMember> page = agentGroupService.page(pageOf(request), wrapper);

        Set<Long> groupIds = page.getRecords().stream()
                .map(AgentGroupMember::getGroupAgentId).collect(Collectors.toSet());
        Set<Long> memberIds = page.getRecords().stream()
                .map(AgentGroupMember::getMemberAgentId).collect(Collectors.toSet());
        Map<Long, AgentDisplayResolver.Display> groups = nameResolver.resolveAgents(groupIds);
        Map<Long, AgentDisplayResolver.Display> members = nameResolver.resolveAgents(memberIds);

        Page<AdminRecordVO> voPage = emptyVoPage(page);
        voPage.setRecords(page.getRecords().stream().map(member -> {
            AdminRecordVO vo = new AdminRecordVO();
            vo.setData(member);
            AgentDisplayResolver.Display group = groups.get(member.getGroupAgentId());
            if (group != null) {
                vo.setGroupName(group.name());
            }
            AgentDisplayResolver.Display agent = members.get(member.getMemberAgentId());
            if (agent != null) {
                vo.setAgentName(agent.name());
                vo.setAgentAvatar(agent.avatar());
                vo.setAgentIcon(agent.icon());
            }
            return vo;
        }).toList());
        return ResultUtils.success(voPage);
    }

    /**
     * 添加群聊成员：仅允许 群聊(type=2) + 智能体(type=1) 组合，重复添加拒绝
     */
    @PostMapping("/group-members")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addGroupMember(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null || request.getGroupAgentId() == null || request.getMemberAgentId() == null,
                ErrorCode.PARAMS_ERROR, "群聊与成员不能为空");
        com.mio.ai.resource.model.entity.Agent group = agentService.getById(request.getGroupAgentId());
        ThrowUtils.throwIf(group == null || group.getType() == null || group.getType() != 2,
                ErrorCode.PARAMS_ERROR, "目标不是群聊智能体");
        com.mio.ai.resource.model.entity.Agent member = agentService.getById(request.getMemberAgentId());
        ThrowUtils.throwIf(member == null || member.getType() == null || member.getType() != 1,
                ErrorCode.PARAMS_ERROR, "成员必须是普通智能体");
        long exists = agentGroupService.count(new QueryWrapper<AgentGroupMember>()
                .eq("group_agent_id", request.getGroupAgentId())
                .eq("member_agent_id", request.getMemberAgentId()));
        ThrowUtils.throwIf(exists > 0, ErrorCode.PARAMS_ERROR, "该成员已在群聊中");
        AgentGroupMember row = new AgentGroupMember();
        row.setGroupAgentId(request.getGroupAgentId());
        row.setMemberAgentId(request.getMemberAgentId());
        row.setCreateTime(new java.util.Date());
        boolean ok = agentGroupService.save(row);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR, "添加失败");
        return ResultUtils.success(row.getId());
    }

    @DeleteMapping("/group-members/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteGroupMember(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "记录 id 不合法");
        ThrowUtils.throwIf(!agentGroupService.removeById(id), ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    // ---------- 智能体版本 ----------

    @PostMapping("/versions/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchVersions(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");
        QueryWrapper<AgentVersion> wrapper = new QueryWrapper<>();
        wrapper.eq(request.getAgentId() != null, "agent_id", request.getAgentId());
        wrapper.eq(request.getUserId() != null, "user_id", request.getUserId());
        wrapper.like(StrUtil.isNotBlank(request.getKeyword()), "version", request.getKeyword());
        wrapper.orderByDesc("create_time");
        Page<AgentVersion> page = agentVersionService.page(pageOf(request), wrapper);

        Map<Long, AgentDisplayResolver.Display> agents = nameResolver.resolveAgents(
                page.getRecords().stream().map(AgentVersion::getAgentId).collect(Collectors.toSet()));

        Page<AdminRecordVO> voPage = emptyVoPage(page);
        voPage.setRecords(page.getRecords().stream().map(version -> {
            AdminRecordVO vo = new AdminRecordVO();
            vo.setData(version);
            AgentDisplayResolver.Display agent = agents.get(version.getAgentId());
            if (agent != null) {
                vo.setAgentName(agent.name());
                vo.setAgentAvatar(agent.avatar());
            }
            return vo;
        }).toList());
        return ResultUtils.success(voPage);
    }

    @DeleteMapping("/versions/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteVersion(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "记录 id 不合法");
        ThrowUtils.throwIf(!agentVersionService.removeById(id), ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    // ---------- 用户个性化配置 ----------

    @PostMapping("/user-configs/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchUserConfigs(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");
        QueryWrapper<AgentUserConfig> wrapper = new QueryWrapper<>();
        wrapper.eq(request.getUserId() != null, "user_id", request.getUserId());
        wrapper.eq(request.getAgentId() != null, "agent_id", request.getAgentId());
        wrapper.eq(request.getStatus() != null, "is_public", request.getStatus());
        wrapper.orderByDesc("update_time");
        Page<AgentUserConfig> page = agentUserConfigService.page(pageOf(request), wrapper);

        Map<Long, AdminNameResolver.UserDisplay> users = nameResolver.resolveUsers(
                page.getRecords().stream().map(AgentUserConfig::getUserId).collect(Collectors.toSet()));
        Map<Long, AgentDisplayResolver.Display> agents = nameResolver.resolveAgents(
                page.getRecords().stream().map(AgentUserConfig::getAgentId).collect(Collectors.toSet()));

        Page<AdminRecordVO> voPage = emptyVoPage(page);
        voPage.setRecords(page.getRecords().stream().map(config -> {
            AdminRecordVO vo = new AdminRecordVO();
            vo.setData(config);
            AdminNameResolver.UserDisplay user = users.get(config.getUserId());
            if (user != null) {
                vo.setUserName(user.name());
                vo.setUserAvatar(user.avatar());
            }
            AgentDisplayResolver.Display agent = agents.get(config.getAgentId());
            if (agent != null) {
                vo.setAgentName(agent.name());
                vo.setAgentAvatar(agent.avatar());
            }
            return vo;
        }).toList());
        return ResultUtils.success(voPage);
    }

    @DeleteMapping("/user-configs/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUserConfig(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "记录 id 不合法");
        ThrowUtils.throwIf(!agentUserConfigService.removeById(id), ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    // ---------- 后台任务 ----------

    @PostMapping("/tasks/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchTasks(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");
        QueryWrapper<AgentBackgroundTask> wrapper = new QueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(request.getTaskStatus()), "status", request.getTaskStatus());
        wrapper.eq(StrUtil.isNotBlank(request.getType()), "type", request.getType());
        wrapper.eq(StrUtil.isNotBlank(request.getConversationId()), "chat_id", request.getConversationId());
        if (StrUtil.isNotBlank(request.getKeyword())) {
            wrapper.and(w -> w.like("description", request.getKeyword()).or().like("task_id", request.getKeyword()));
        }
        wrapper.orderByDesc("create_time");
        Page<AgentBackgroundTask> page = backgroundTaskMapper.selectPage(pageOf(request), wrapper);

        Page<AdminRecordVO> voPage = emptyVoPage(page);
        voPage.setRecords(page.getRecords().stream().map(task -> {
            AdminRecordVO vo = new AdminRecordVO();
            vo.setData(task);
            return vo;
        }).toList());
        return ResultUtils.success(voPage);
    }

    @DeleteMapping("/tasks/{taskId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteTask(@PathVariable String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不合法");
        AgentBackgroundTask task = backgroundTaskMapper.selectById(taskId);
        ThrowUtils.throwIf(task == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        if ("running".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务运行中，禁止删除");
        }
        backgroundTaskMapper.deleteById(taskId);
        return ResultUtils.success(true);
    }

    private <T> Page<T> pageOf(AdminRecordQueryRequest q) {
        Page<T> page = new Page<>();
        page.setCurrent(q.getCurrent());
        page.setSize(q.getPageSize());
        return page;
    }

    private Page<AdminRecordVO> emptyVoPage(Page<?> source) {
        return new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
    }
}
