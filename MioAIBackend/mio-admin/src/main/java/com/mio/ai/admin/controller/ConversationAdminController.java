package com.mio.ai.admin.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.admin.component.AdminNameResolver;
import com.mio.ai.admin.model.vo.AdminRecordVO;
import com.mio.ai.bot.mapper.AgentMessageMapper;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.bot.mapper.ChatFileMapper;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.model.entity.ChatFileDO;
import com.mio.ai.bot.controller.MioBotController;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.service.ChatCascadeDeleteService;
import com.mio.ai.bot.service.ChatFileService;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.admin.model.dto.AdminRecordQueryRequest;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员会话数据管理接口：会话 / 消息 / 聊天文件
 */
@Slf4j
@RestController
@RequestMapping("/admin/conversations")
public class ConversationAdminController {

    @Resource
    private ChatConversationDOMapper conversationMapper;

    @Resource
    private AgentMessageMapper agentMessageMapper;

    @Resource
    private ChatFileMapper chatFileMapper;

    @Resource
    private AgentMessageService agentMessageService;

    @Resource
    private ChatFileService chatFileService;

    @Resource
    private ChatCascadeDeleteService chatCascadeDeleteService;

    @Resource
    private AdminNameResolver nameResolver;

    @Resource
    private com.mio.ai.user.service.UserService userService;

    @Resource
    private com.mio.ai.resource.service.agent.AgentService agentService;

    @Resource
    private com.mio.ai.resource.service.log.RagRetrievalLogService ragRetrievalLogService;

    @Resource
    private com.mio.ai.resource.service.log.ToolCallLogService toolCallLogService;

    @Resource
    private com.mio.ai.resource.service.log.SkillUsageLogService skillUsageLogService;

    @Resource
    private com.mio.ai.resource.service.mcp.McpToolService mcpToolService;

    /**
     * 分页查询会话列表
     */
    @PostMapping("/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchConversations(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");

        QueryWrapper<ChatConversationDO> wrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(request.getKeyword())) {
            wrapper.and(w -> w.like("title", request.getKeyword())
                    .or().like("conversation_id", request.getKeyword()));
        }
        wrapper.eq(request.getUserId() != null, "user_id", request.getUserId());
        wrapper.eq(request.getAgentId() != null, "agent_id", request.getAgentId());
        wrapper.eq(request.getPinned() != null, "pinned", request.getPinned());
        wrapper.ge(StrUtil.isNotBlank(request.getStartDate()), "create_time", request.getStartDate());
        wrapper.le(StrUtil.isNotBlank(request.getEndDate()), "create_time", request.getEndDate());
        if (StrUtil.isNotBlank(request.getOwnerName())) {
            List<Long> ownerIds = userService.list(new QueryWrapper<com.mio.ai.user.model.entity.User>()
                    .like("user_name", request.getOwnerName())).stream().map(com.mio.ai.user.model.entity.User::getId).toList();
            wrapper.in(ownerIds.isEmpty(), "user_id", List.of(-1L));
            wrapper.in(!ownerIds.isEmpty(), "user_id", ownerIds);
        }
        if (StrUtil.isNotBlank(request.getAgentName())) {
            List<Long> agentIds = agentService.list(new QueryWrapper<com.mio.ai.resource.model.entity.Agent>()
                    .like("name", request.getAgentName())).stream().map(com.mio.ai.resource.model.entity.Agent::getId).toList();
            wrapper.in(agentIds.isEmpty(), "agent_id", List.of(-1L));
            wrapper.in(!agentIds.isEmpty(), "agent_id", agentIds);
        }
        if (StrUtil.isNotBlank(request.getMessageKeyword())) {
            String kw = request.getMessageKeyword().replace("'", "''");
            wrapper.inSql("conversation_id", "SELECT conversation_id COLLATE utf8mb4_unicode_ci FROM agent_message WHERE blocks LIKE '%" + kw + "%'");
        }
        wrapper.orderByDesc("update_time");

        Page<ChatConversationDO> page = conversationMapper.selectPage(
                new Page<>(request.getCurrent(), request.getPageSize()), wrapper);
        return ResultUtils.success(wrapConversationPage(page));
    }

    /**
     * 分页查询会话内消息（按 seq 升序）
     */
    @GetMapping("/{conversationId}/messages")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AgentMessageDO>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize) {
        ThrowUtils.throwIf(StrUtil.isBlank(conversationId), ErrorCode.PARAMS_ERROR, "会话 id 不合法");
        ThrowUtils.throwIf(pageSize > 200, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 200");
        Page<AgentMessageDO> page = agentMessageMapper.selectPage(
                new Page<>(current, pageSize),
                new QueryWrapper<AgentMessageDO>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("seq"));
        return ResultUtils.success(page);
    }

    /**
     * 删除会话（级联清理消息、版本归档、文件与沙箱目录）
     */
    @DeleteMapping("/{conversationId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteConversation(@PathVariable String conversationId) {
        ThrowUtils.throwIf(StrUtil.isBlank(conversationId), ErrorCode.PARAMS_ERROR, "会话 id 不合法");
        if (MioBotController.hasActiveTurn(conversationId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "会话有执行中的轮次，请稍后再删");
        }
        chatCascadeDeleteService.deleteConversationCompletely(conversationId);
        return ResultUtils.success(true);
    }

    /**
     * 删除单条消息
     */
    @DeleteMapping("/{conversationId}/messages/{messageId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteMessage(@PathVariable String conversationId, @PathVariable Long messageId) {
        ThrowUtils.throwIf(messageId == null || messageId <= 0, ErrorCode.PARAMS_ERROR, "消息 id 不合法");
        agentMessageService.deleteById(messageId);
        return ResultUtils.success(true);
    }

    /**
     * 会话关联文件列表
     */
    @GetMapping("/{conversationId}/files")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<ChatFileDO>> listFiles(@PathVariable String conversationId) {
        ThrowUtils.throwIf(StrUtil.isBlank(conversationId), ErrorCode.PARAMS_ERROR, "会话 id 不合法");
        return ResultUtils.success(chatFileMapper.selectList(
                new QueryWrapper<ChatFileDO>()
                        .eq("conversation_id", conversationId)
                        .orderByDesc("create_time")));
    }

    /**
     * 删除聊天文件（清理台账并删源文件）
     */
    @DeleteMapping("/files/{fileId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteFile(@PathVariable Long fileId) {
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.PARAMS_ERROR, "文件 id 不合法");
        ChatFileDO file = chatFileMapper.selectById(fileId);
        ThrowUtils.throwIf(file == null, ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        chatFileService.deleteByPath(file.getPath());
        return ResultUtils.success(true);
    }

    /**
     * 按使用记录聚合关联会话：知识库（检索）/ MCP（工具调用）/ 技能（使用）三种关联，
     * 返回去重后的会话列表（含最后使用时间；会话本体已删除的条目标记 exists=false）
     */
    @PostMapping("/by-usage")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AdminRecordVO>> searchByUsage(@RequestBody AdminRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getPageSize() > 100, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 100");
        ThrowUtils.throwIf(request.getKbId() == null && request.getMcpId() == null && request.getSkillId() == null,
                ErrorCode.PARAMS_ERROR, "请指定知识库 / MCP / 技能之一");

        // 1. 从对应日志表聚合 (conversation_id, last_time, user_id)
        QueryWrapper<?> usageWrapper = null;
        Class<?> entityClass = null;
        if (request.getKbId() != null) {
            QueryWrapper<com.mio.ai.resource.model.entity.RagRetrievalLog> w = new QueryWrapper<>();
            w.eq("kb_id", request.getKbId());
            entityClass = com.mio.ai.resource.model.entity.RagRetrievalLog.class;
            usageWrapper = w;
        } else if (request.getMcpId() != null) {
            com.mio.ai.resource.model.entity.McpTool mcpTool = mcpToolService.getById(request.getMcpId());
            ThrowUtils.throwIf(mcpTool == null, ErrorCode.NOT_FOUND_ERROR, "MCP 工具不存在");
            String server = com.mio.ai.resource.service.mcp.impl.McpClientManagerServiceImpl
                    .firstServerKey(mcpTool.getConfig(), mcpTool.getName());
            QueryWrapper<com.mio.ai.resource.model.entity.ToolCallLog> w = new QueryWrapper<>();
            w.likeRight("name", "mcp__"
                    + com.mio.ai.resource.service.mcp.impl.McpClientManagerServiceImpl.sanitizeSegment(server) + "__");
            entityClass = com.mio.ai.resource.model.entity.ToolCallLog.class;
            usageWrapper = w;
        } else {
            QueryWrapper<com.mio.ai.resource.model.entity.SkillUsageLog> w = new QueryWrapper<>();
            w.eq("skill_id", request.getSkillId());
            entityClass = com.mio.ai.resource.model.entity.SkillUsageLog.class;
            usageWrapper = w;
        }
        usageWrapper.select("conversation_id", "MAX(create_time) AS last_time", "MAX(user_id) AS user_id")
                .isNotNull("conversation_id")
                .groupBy("conversation_id");
        List<Map<String, Object>> usageRows = logQueryMaps(entityClass, usageWrapper);

        // 2. 按最后使用时间倒序 + 内存分页
        usageRows.sort((a, b) -> Comparable.class.cast(b.get("last_time") == null ? "" : b.get("last_time"))
                .compareTo(Comparable.class.cast(a.get("last_time") == null ? "" : a.get("last_time"))));
        long total = usageRows.size();
        int from = (int) Math.min((request.getCurrent() - 1) * request.getPageSize(), total);
        int to = (int) Math.min(from + request.getPageSize(), total);
        List<Map<String, Object>> pageRows = usageRows.subList(from, to);

        // 3. 回查会话本体（可能已删除）+ 用户名
        List<String> chatIds = pageRows.stream().map(r -> String.valueOf(r.get("conversation_id"))).toList();
        Map<String, ChatConversationDO> convMap = chatIds.isEmpty() ? Map.of()
                : conversationMapper.selectList(new QueryWrapper<ChatConversationDO>()
                        .in("conversation_id", chatIds)).stream()
                        .collect(Collectors.toMap(ChatConversationDO::getConversationId, c -> c, (a, b) -> a));
        Set<Long> userIds = pageRows.stream()
                .map(r -> r.get("user_id") == null ? null : Long.valueOf(String.valueOf(r.get("user_id"))))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminNameResolver.UserDisplay> users = nameResolver.resolveUsers(userIds);

        List<AdminRecordVO> rows = pageRows.stream().map(usage -> {
            String chatId = String.valueOf(usage.get("conversation_id"));
            ChatConversationDO conv = convMap.get(chatId);
            AdminRecordVO vo = new AdminRecordVO();
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("conversationId", chatId);
            data.put("exists", conv != null);
            data.put("title", conv != null ? conv.getTitle() : "（会话已删除，仅剩使用记录）");
            data.put("agentId", conv != null ? conv.getAgentId() : null);
            data.put("pinned", conv != null ? conv.getPinned() : null);
            data.put("updateTime", conv != null ? conv.getUpdateTime() : null);
            Long usageUserId = usage.get("user_id") == null ? null : Long.valueOf(String.valueOf(usage.get("user_id")));
            data.put("userId", conv != null && conv.getUserId() != null ? conv.getUserId() : usageUserId);
            data.put("lastUsedTime", usage.get("last_time"));
            vo.setData(data);
            Long displayUserId = ((Number) data.get("userId")) == null ? null : ((Number) data.get("userId")).longValue();
            if (displayUserId != null) {
                AdminNameResolver.UserDisplay user = users.get(displayUserId);
                if (user != null) {
                    vo.setUserName(user.name());
                    vo.setUserAvatar(user.avatar());
                }
            }
            if (conv != null) {
                AgentDisplayResolver.Display agent = nameResolver.resolveAgents(List.of(conv.getAgentId()))
                        .get(conv.getAgentId());
                if (agent != null) {
                    vo.setAgentName(agent.name());
                    vo.setAgentAvatar(agent.avatar());
                }
            }
            return vo;
        }).collect(Collectors.toList());

        Page<AdminRecordVO> voPage = new Page<>(request.getCurrent(), request.getPageSize(), total);
        voPage.setRecords(rows);
        return ResultUtils.success(voPage);
    }

    /** 按实体类型路由的 selectMaps（聚合查询走原生 wrapper） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> logQueryMaps(Class<?> entityClass, QueryWrapper wrapper) {
        if (entityClass == com.mio.ai.resource.model.entity.RagRetrievalLog.class) {
            return ragRetrievalLogService.listMaps(wrapper);
        }
        if (entityClass == com.mio.ai.resource.model.entity.ToolCallLog.class) {
            return toolCallLogService.listMaps(wrapper);
        }
        return skillUsageLogService.listMaps(wrapper);
    }

    private Page<AdminRecordVO> wrapConversationPage(Page<ChatConversationDO> page) {
        List<ChatConversationDO> records = page.getRecords();
        Map<Long, AdminNameResolver.UserDisplay> users = nameResolver.resolveUsers(
                records.stream().map(ChatConversationDO::getUserId).collect(Collectors.toSet()));
        Map<Long, AgentDisplayResolver.Display> agents = nameResolver.resolveAgents(
                records.stream().map(ChatConversationDO::getAgentId).collect(Collectors.toSet()));
        Page<AdminRecordVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(records.stream().map(conv -> {
            AdminRecordVO vo = new AdminRecordVO();
            vo.setData(conv);
            AdminNameResolver.UserDisplay user = users.get(conv.getUserId());
            if (user != null) {
                vo.setUserName(user.name());
                vo.setUserAvatar(user.avatar());
            }
            AgentDisplayResolver.Display agent = agents.get(conv.getAgentId());
            if (agent != null) {
                vo.setAgentName(agent.name());
                vo.setAgentAvatar(agent.avatar());
            }
            return vo;
        }).toList());
        return voPage;
    }
}
