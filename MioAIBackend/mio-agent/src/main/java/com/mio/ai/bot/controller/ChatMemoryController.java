package com.mio.ai.bot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.user.model.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
