package com.mio.ai.superagent.controller;

import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.superagent.app.CSApp;
import com.mio.ai.superagent.model.dto.ChatMessageRequest;
import com.mio.ai.superagent.model.entity.ChatConversationDO;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.model.vo.MessageVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import com.mio.ai.user.model.vo.LoginUserVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/3/29 20:54
 * @description:
 */

@RequestMapping("/cs")
@RestController
public class CSAppController {

    @Resource
    private CSApp CSApp;

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    RedisComponent redisComponent;

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param chatMessageRequest
     * @return
     */
    @PostMapping("/chat")
    @LogInfo
    public SseEmitter doChat(@RequestBody ChatMessageRequest chatMessageRequest, HttpServletRequest request) {
        // 创建 ChatVO
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatMessageRequest.getChatId());
        chatVO.setMessage(chatMessageRequest.getContent());
        chatVO.setAgentId(chatMessageRequest.getAgentId());
        chatVO.setUserId(getUserId(request.getHeader("token")));

        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(45000L); // 1.5 分钟超时
        // 获取 Flux 响应式数据流并且直接通过订阅推送给 SseEmitter
        CSApp.doChat(chatVO)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        // 返回
        return sseEmitter;
    }

    /**
     * 获取会话列表
     * @return
     */
    @GetMapping("/getChatIds")
    @LogInfo
    public BaseResponse<List<ChatConversationDO>> getChatIds(@RequestParam String agentId, HttpServletRequest request){
        return ResultUtils
                .success(chatHistoryRepository.getChats(getUserId(request.getHeader("token")), agentId));
    }

    /**
     * 获取会话记录
     * @param chatId
     */
    @GetMapping("/getChatHistory")
    @LogInfo
    public BaseResponse<List<MessageVO>> getChatHistory(@RequestParam String chatId){
        List<Message> messages = chatMemory.get(chatId);
        return ResultUtils
                .success(messages.stream().map(MessageVO::new).collect(Collectors.toList()));
    }

    /**
     * 删除会话
     * @param chatId
     * @return
     */
    @PostMapping("/deleteChat")
    @LogInfo
    public BaseResponse<?> deleteChat(@RequestParam String chatId){
        try {
            chatHistoryRepository.clearByChatId(chatId);
            chatMemory.clear(chatId);
        } catch (Exception e){
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
        return ResultUtils.success(true);
    }

    private Long getUserId(String token) {
        LoginUserVO currentUser = redisComponent.getUserInfoByToken(token);
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser.getId();
    }
}
