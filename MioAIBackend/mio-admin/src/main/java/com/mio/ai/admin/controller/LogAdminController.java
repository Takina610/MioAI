package com.mio.ai.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.admin.component.AdminNameResolver;
import com.mio.ai.admin.model.dto.AdminRecordQueryRequest;
import com.mio.ai.admin.model.vo.AdminRecordVO;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.RagRetrievalLog;
import com.mio.ai.resource.model.entity.SkillUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.model.entity.UserActivityLog;
import com.mio.ai.resource.model.entity.UserStatsDaily;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.RagRetrievalLogService;
import com.mio.ai.resource.service.log.SkillUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.resource.service.log.UserActivityLogService;
import com.mio.ai.resource.service.log.UserStatsDailyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员日志记录管理接口：六类留痕记录（对话用量/技能调用/工具调用/RAG 检索/用户活动/每日用量）
 * 统一 /admin/logs/{type}/search 与 /admin/logs/{type}/{id}，记录本体放 AdminRecordVO.data
 */
@Slf4j
@RestController
@RequestMapping("/admin/logs")
public class LogAdminController {

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @Resource
    private SkillUsageLogService skillUsageLogService;

    @Resource
    private ToolCallLogService toolCallLogService;

    @Resource
    private RagRetrievalLogService ragRetrievalLogService;

    @Resource
    private UserActivityLogService userActivityLogService;

    @Resource
    private UserStatsDailyService userStatsDailyService;

    @Resource
    private com.mio.ai.resource.service.mcp.McpToolService mcpToolService;

    @Resource
    private AdminNameResolver nameResolver;

    @PostMapping("/{type}/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchLogs(
            @PathVariable String type, @RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");
        Page<AdminRecordVO> page = switch (type) {
            case "usage" -> searchUsage(request);
            case "skill" -> searchSkill(request);
            case "tool" -> searchTool(request);
            case "rag" -> searchRag(request);
            case "activity" -> searchActivity(request);
            case "daily" -> searchDaily(request);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的日志类型");
        };
        return ResultUtils.success(page);
    }

    @DeleteMapping("/{type}/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteLog(@PathVariable String type, @PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "记录 id 不合法");
        IService<?> service = switch (type) {
            case "usage" -> agentUsageLogService;
            case "skill" -> skillUsageLogService;
            case "tool" -> toolCallLogService;
            case "rag" -> ragRetrievalLogService;
            case "activity" -> userActivityLogService;
            case "daily" -> userStatsDailyService;
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的日志类型");
        };
        boolean ok = service.removeById(id);
        ThrowUtils.throwIf(!ok, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }

    private Page<AdminRecordVO> searchUsage(AdminRecordQueryRequest q) {
        QueryWrapper<AgentUsageLog> wrapper = new QueryWrapper<>();
        wrapper.eq(q.getUserId() != null, "user_id", q.getUserId());
        wrapper.eq(q.getAgentId() != null, "agent_id", q.getAgentId());
        wrapper.eq(q.getStatus() != null, "status", q.getStatus());
        wrapper.eq(StrUtil.isNotBlank(q.getConversationId()), "conversation_id", q.getConversationId());
        wrapper.like(StrUtil.isNotBlank(q.getModel()), "model", q.getModel());
        wrapper.like(StrUtil.isNotBlank(q.getKeyword()), "error_msg", q.getKeyword());
        applyTimeRange(wrapper, q);
        wrapper.orderByDesc("create_time");
        Page<AgentUsageLog> page = agentUsageLogService.page(pageOf(q), wrapper);
        return wrap(page, AgentUsageLog::getUserId, AgentUsageLog::getAgentId, null);
    }

    private Page<AdminRecordVO> searchSkill(AdminRecordQueryRequest q) {
        QueryWrapper<SkillUsageLog> wrapper = new QueryWrapper<>();
        wrapper.eq(q.getUserId() != null, "user_id", q.getUserId());
        wrapper.eq(q.getAgentId() != null, "agent_id", q.getAgentId());
        wrapper.eq(q.getSkillId() != null, "skill_id", q.getSkillId());
        wrapper.eq(StrUtil.isNotBlank(q.getConversationId()), "conversation_id", q.getConversationId());
        wrapper.like(StrUtil.isNotBlank(q.getKeyword()), "skill_name", q.getKeyword());
        wrapper.orderByDesc("create_time");
        Page<SkillUsageLog> page = skillUsageLogService.page(pageOf(q), wrapper);
        return wrap(page, SkillUsageLog::getUserId, SkillUsageLog::getAgentId, null);
    }

    private Page<AdminRecordVO> searchTool(AdminRecordQueryRequest q) {
        QueryWrapper<ToolCallLog> wrapper = new QueryWrapper<>();
        wrapper.eq(q.getUserId() != null, "user_id", q.getUserId());
        wrapper.eq(q.getAgentId() != null, "agent_id", q.getAgentId());
        wrapper.eq(q.getStatus() != null, "status", q.getStatus());
        wrapper.eq(StrUtil.isNotBlank(q.getConversationId()), "conversation_id", q.getConversationId());
        wrapper.like(StrUtil.isNotBlank(q.getKeyword()), "name", q.getKeyword());
        // 按 MCP 工具关联：解析该工具注册时的调用名前缀 mcp__<server>__
        if (q.getMcpId() != null) {
            com.mio.ai.resource.model.entity.McpTool mcpTool = mcpToolService.getById(q.getMcpId());
            if (mcpTool != null) {
                String server = com.mio.ai.resource.service.mcp.impl.McpClientManagerServiceImpl
                        .firstServerKey(mcpTool.getConfig(), mcpTool.getName());
                wrapper.likeRight("name", "mcp__"
                        + com.mio.ai.resource.service.mcp.impl.McpClientManagerServiceImpl.sanitizeSegment(server) + "__");
            } else {
                wrapper.eq("name", "__no_such_mcp__");
            }
        }
        applyTimeRange(wrapper, q);
        wrapper.orderByDesc("create_time");
        Page<ToolCallLog> page = toolCallLogService.page(pageOf(q), wrapper);
        return wrap(page, ToolCallLog::getUserId, ToolCallLog::getAgentId, null);
    }

    private Page<AdminRecordVO> searchRag(AdminRecordQueryRequest q) {
        QueryWrapper<RagRetrievalLog> wrapper = new QueryWrapper<>();
        wrapper.eq(q.getUserId() != null, "user_id", q.getUserId());
        wrapper.eq(q.getAgentId() != null, "agent_id", q.getAgentId());
        wrapper.eq(q.getKbId() != null, "kb_id", q.getKbId());
        wrapper.eq(StrUtil.isNotBlank(q.getConversationId()), "conversation_id", q.getConversationId());
        wrapper.like(StrUtil.isNotBlank(q.getModel()), "model", q.getModel());
        wrapper.like(StrUtil.isNotBlank(q.getKeyword()), "query", q.getKeyword());
        applyTimeRange(wrapper, q);
        wrapper.orderByDesc("create_time");
        Page<RagRetrievalLog> page = ragRetrievalLogService.page(pageOf(q), wrapper);
        return wrap(page, RagRetrievalLog::getUserId, RagRetrievalLog::getAgentId, null);
    }

    private Page<AdminRecordVO> searchActivity(AdminRecordQueryRequest q) {
        QueryWrapper<UserActivityLog> wrapper = new QueryWrapper<>();
        wrapper.eq(q.getUserId() != null, "user_id", q.getUserId());
        wrapper.eq(StrUtil.isNotBlank(q.getModule()), "module", q.getModule());
        wrapper.eq(q.getStatus() != null, "success", q.getStatus());
        if (StrUtil.isNotBlank(q.getKeyword())) {
            wrapper.and(w -> w.like("action", q.getKeyword()).or().like("path", q.getKeyword()));
        }
        applyTimeRange(wrapper, q);
        wrapper.orderByDesc("create_time");
        Page<UserActivityLog> page = userActivityLogService.page(pageOf(q), wrapper);
        return wrap(page, UserActivityLog::getUserId, null, null);
    }

    private Page<AdminRecordVO> searchDaily(AdminRecordQueryRequest q) {
        QueryWrapper<UserStatsDaily> wrapper = new QueryWrapper<>();
        wrapper.eq(q.getUserId() != null, "user_id", q.getUserId());
        wrapper.ge(StrUtil.isNotBlank(q.getStartDate()), "stat_date", q.getStartDate());
        wrapper.le(StrUtil.isNotBlank(q.getEndDate()), "stat_date", q.getEndDate());
        wrapper.orderByDesc("stat_date");
        Page<UserStatsDaily> page = userStatsDailyService.page(pageOf(q), wrapper);
        return wrap(page, UserStatsDaily::getUserId, null, null);
    }

    private <T> Page<AdminRecordVO> wrap(Page<T> page,
                                         Function<T, Long> userIdGetter,
                                         Function<T, Long> agentIdGetter,
                                         Function<T, Long> groupIdGetter) {
        List<T> records = page.getRecords();
        Set<Long> userIds = collectIds(records, userIdGetter);
        Set<Long> agentIds = collectIds(records, agentIdGetter);
        Set<Long> groupIds = collectIds(records, groupIdGetter);
        Map<Long, AdminNameResolver.UserDisplay> users = nameResolver.resolveUsers(userIds);
        Map<Long, AgentDisplayResolver.Display> agents = nameResolver.resolveAgents(agentIds);
        Map<Long, AgentDisplayResolver.Display> groups = nameResolver.resolveAgents(groupIds);

        Page<AdminRecordVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records.stream().map(record -> {
            AdminRecordVO vo = new AdminRecordVO();
            vo.setData(record);
            Long userId = userIdGetter.apply(record);
            if (userId != null) {
                AdminNameResolver.UserDisplay user = users.get(userId);
                if (user != null) {
                    vo.setUserName(user.name());
                    vo.setUserAvatar(user.avatar());
                }
            }
            fillAgent(vo, agents, agentIdGetter != null ? agentIdGetter.apply(record) : null);
            Long groupId = groupIdGetter != null ? groupIdGetter.apply(record) : null;
            if (groupId != null) {
                AgentDisplayResolver.Display group = groups.get(groupId);
                if (group != null) {
                    vo.setGroupName(group.name());
                }
            }
            return vo;
        }).toList());
        return voPage;
    }

    private void fillAgent(AdminRecordVO vo, Map<Long, AgentDisplayResolver.Display> agents, Long agentId) {
        if (agentId == null) {
            return;
        }
        AgentDisplayResolver.Display agent = agents.get(agentId);
        if (agent != null) {
            vo.setAgentName(agent.name());
            vo.setAgentAvatar(agent.avatar());
        }
    }

    private <R> Set<Long> collectIds(List<R> records, Function<R, Long> getter) {
        if (getter == null) {
            return Collections.emptySet();
        }
        return records.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <T> Page<T> pageOf(AdminRecordQueryRequest q) {
        Page<T> page = new Page<>();
        page.setCurrent(q.getCurrent());
        page.setSize(q.getPageSize());
        return page;
    }

    /** 通用创建时间范围（daily 类型用 stat_date，不走这里） */
    private void applyTimeRange(QueryWrapper<?> wrapper, AdminRecordQueryRequest q) {
        wrapper.ge(StrUtil.isNotBlank(q.getStartDate()), "create_time", q.getStartDate());
        wrapper.le(StrUtil.isNotBlank(q.getEndDate()), "create_time", q.getEndDate());
    }
}

