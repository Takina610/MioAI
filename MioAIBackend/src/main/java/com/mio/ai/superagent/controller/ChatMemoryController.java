package com.mio.ai.superagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;import com.mio.ai.superagent.model.entity.ChatConversationDO;
import com.mio.ai.superagent.model.vo.MessageVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import com.mio.ai.user.model.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/3/31 19:21
 * @description:
 */

@RequestMapping("/memory")
@RestController
public class ChatMemoryController {

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    RedisComponent redisComponent;


    /**
     * 获取会话列表
     * @return
     */
    @GetMapping("/getChatIds/{agentId}")
    @LogInfo
    public BaseResponse<List<ChatConversationDO>> getChatIds(@PathVariable String agentId, HttpServletRequest request){
        return ResultUtils
                .success(chatHistoryRepository
                        .getChats(redisComponent.getUserId(request.getHeader("token")), agentId));
    }

    /**
     * 分页获取会话列表
     * @param agentId 智能体ID
     * @param current 当前页
     * @param size 每页大小
     * @param request HTTP请求
     * @return 分页会话列表
     */
    @GetMapping("/getChatIdsPage/{agentId}")
    @LogInfo
    public BaseResponse<Page<ChatConversationDO>> getChatIdsPage(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            HttpServletRequest request) {
        return ResultUtils
            .success(chatHistoryRepository
            .getChatsPage(redisComponent.getUserId(request.getHeader("token")), agentId, current, size));
    }

    /**
     * 获取会话记录（仅会话所有者可读）
     * @param chatId
     */
    @GetMapping("/getChatHistory/{chatId}")
    @LogInfo
    public BaseResponse<List<MessageVO>> getChatHistory(@PathVariable String chatId, HttpServletRequest request){
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(chatId, userId);
        List<Message> messages = chatMemory.get(chatId);
        if (messages.isEmpty()) {
            return ResultUtils.success(null);
        }
        return ResultUtils
                .success(messages.stream()
                        .map(MessageVO::new)
                        .filter(vo -> !vo.isToolMessage())
                        .collect(Collectors.toList()));
    }

    /**
     * 删除会话（仅会话所有者可删）
     * @param chatId
     * @return
     */
    @PostMapping("/deleteChat/{chatId}")
    @LogInfo
    public BaseResponse<?> deleteChat(@PathVariable String chatId, HttpServletRequest request){
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        checkChatOwner(chatId, userId);
        try {
            chatHistoryRepository.clearByChatId(chatId);
            chatMemory.clear(chatId);
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
