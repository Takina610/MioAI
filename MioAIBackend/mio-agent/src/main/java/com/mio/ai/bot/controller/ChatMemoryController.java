package com.mio.ai.bot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.user.model.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/3/31 19:21
 * @description:
 */

@RequestMapping("/memory")
@RestController
public class ChatMemoryController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ChatMemoryController.class);

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    RedisComponent redisComponent;

    @Autowired
    com.mio.ai.bot.service.ChatCascadeDeleteService chatCascadeDeleteService;

    @Autowired
    com.mio.ai.resource.service.agent.AgentService agentService;

    @Autowired
    AgentMessageService agentMessageService;


    /**
     * 获取会话列表
     */
    @GetMapping("/getChatIds/{agentId}")
    @LogInfo
    public BaseResponse<List<ChatConversationDO>> getChatIds(@PathVariable Long agentId, HttpServletRequest request){
        return ResultUtils
                .success(chatHistoryRepository
                        .getChats(redisComponent.getUserId(request.getHeader("token")), agentId));
    }

    /**
     * 分页获取会话列表
     * @param current 当前页
     * @param size 每页大小
     * @param keyword 标题搜索关键字（可空）
     * @param request HTTP请求
     * @return 分页会话列表
     */
    @GetMapping("/getChatIdsPage/{agentId}")
    @LogInfo
    public BaseResponse<Page<ChatConversationDO>> getChatIdsPage(
            @PathVariable Long agentId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        return ResultUtils
            .success(chatHistoryRepository
            .getChatsPage(redisComponent.getUserId(request.getHeader("token")), agentId, current, size, keyword));
    }

    /**
     * 删除会话（仅会话所有者可删）：清会话行、对话记忆、消息流水与版本归档；
     * 沙箱暂存文件（uploads/outputs 的 &lt;chatId&gt; 目录）按 chat_file 台账异步清理。
     * 工具调用日志等审计数据持久保留，不随会话删除。
     * @param chatId
     * @return
     */
    @PostMapping("/deleteChat/{chatId}")
    @LogInfo
    public BaseResponse<?> deleteChat(@PathVariable String chatId, HttpServletRequest request){
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(chatId, userId);
        // 执行中的会话禁止删除：与截断同一规则——删后落库行会把已删数据写回
        if (MioBotController.hasActiveTurn(chatId)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话正在执行中，请等待本轮完成后再删除");
        }
        try {
            chatCascadeDeleteService.deleteConversationCompletely(chatId);
        } catch (Exception e){
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
        return ResultUtils.success(true);
    }

    /**
     * 根据会话ID获取会话信息（仅会话所有者可读）
     * @param conversationId 会话ID
     * @return 会话信息
     */
    @GetMapping("/getConversation/{conversationId}")
    @LogInfo
    public BaseResponse<ChatConversationDO> getConversation(@PathVariable String conversationId, HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(conversationId, userId);
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(conversationId);
        if (conversation == null) {
            return ResultUtils.success(null);
        }
        return ResultUtils.success(conversation);
    }

    /**
     * 分享页公开读取：会话标题/时间 + 消息文本块 + 智能体展示信息。
     * 链接即凭证：会话确属该智能体（路径两个 ID 匹配，由所有者分享时给出）即可读；
     * 只返回展示字段（名字/图标/群成员概要），不含系统提示词等敏感配置。
     */
    @GetMapping("/share/{agentId}/{conversationId}")
    @LogInfo
    public BaseResponse<Map<String, Object>> getSharedConversation(@PathVariable Long agentId,
                                                                   @PathVariable String conversationId) {
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(conversationId);
        if (conversation == null || conversation.getAgentId() == null
                || !conversation.getAgentId().equals(agentId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }

        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("title", conversation.getTitle());
        data.put("createTime", conversation.getCreateTime());
        // 私有智能体的分享页也要正确显示身份，展示字段直接随分享数据下发
        AgentVO agent = agentService.getAgentById(agentId);
        if (agent != null) {
            Map<String, Object> agentBrief = new java.util.LinkedHashMap<>();
            agentBrief.put("id", agent.getId());
            agentBrief.put("userId", agent.getUserId());
            agentBrief.put("name", agent.getName());
            agentBrief.put("avatar", agent.getAvatar());
            agentBrief.put("icon", agent.getIcon());
            agentBrief.put("type", agent.getType());
            agentBrief.put("members", agent.getMembers());
            data.put("agent", agentBrief);
        }
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        for (AgentMessageDO row : agentMessageService.listByConversation(conversationId)) {
            // system 行是会话内 UI 提示（如模型切换），不属于对话内容，分享不渲染
            if ("system".equals(row.getRole())) {
                continue;
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("role", row.getRole());
            item.put("seq", row.getSeq());
            item.put("blocks", parseJson(row.getBlocks()));
            item.put("durationMs", row.getDurationMs());
            item.put("createTime", row.getCreateTime());
            messages.add(item);
        }
        data.put("messages", messages);
        return ResultUtils.success(data);
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JacksonUtil.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验会话归属，防止任意用户读取/删除他人会话
     */
    /**
     * 置顶/取消置顶会话（仅会话所有者）：置顶会话排在列表最前（pinned 倒序组内按更新时间）
     */
    @PostMapping("/pinChat/{chatId}")
    @LogInfo
    public BaseResponse<Boolean> pinChat(@PathVariable String chatId,
                                         @RequestParam(defaultValue = "true") boolean pinned,
                                         HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(chatId, userId);
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(chatId);
        chatHistoryRepository.updatePinned(conversation.getId(), pinned ? 1 : 0);
        return ResultUtils.success(true);
    }

    /**
     * 跨智能体的最近会话（输入框 @ 引用数据源）：全量会话按更新时间倒序，
     * 附带归属智能体名/类型供引用列表展示。仅返回本人会话。
     */
    @GetMapping("/allSessions")
    @LogInfo
    public BaseResponse<List<java.util.Map<String, Object>>> allSessions(
            @RequestParam(defaultValue = "200") int limit,
            HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        if (userId == null) {
            return ResultUtils.success(new java.util.ArrayList<>());
        }
        List<ChatConversationDO> chats = chatHistoryRepository.listRecentByUser(userId, Math.min(Math.max(limit, 1), 500));
        java.util.Map<Long, com.mio.ai.resource.model.entity.Agent> agentById = new java.util.HashMap<>();
        try {
            java.util.Set<Long> agentIds = chats.stream()
                    .map(ChatConversationDO::getAgentId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            if (!agentIds.isEmpty()) {
                for (com.mio.ai.resource.model.entity.Agent agent : agentService.listByIds(agentIds)) {
                    agentById.put(agent.getId(), agent);
                }
            }
        } catch (Exception e) {
            log.warn("批量查询会话归属智能体失败: {}", e.getMessage());
        }
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (ChatConversationDO chat : chats) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("conversationId", chat.getConversationId());
            row.put("title", chat.getTitle());
            row.put("agentId", chat.getAgentId());
            row.put("updateTime", chat.getUpdateTime());
            com.mio.ai.resource.model.entity.Agent agent = agentById.get(chat.getAgentId());
            if (agent != null) {
                row.put("agentName", agent.getName());
                row.put("agentType", agent.getType());
            }
            rows.add(row);
        }
        return ResultUtils.success(rows);
    }

    private void checkChatOwner(String chatId, Long userId) {
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(chatId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (conversation.getUserId() == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该会话");
        }
    }
}
