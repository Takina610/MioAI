package com.mio.ai.superagent.controller;

import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.superagent.model.entity.ChatConversationDO;
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
     * 获取会话记录
     * @param chatId
     */
    @GetMapping("/getChatHistory/{chatId}")
    @LogInfo
    public BaseResponse<List<MessageVO>> getChatHistory(@PathVariable String chatId){
        List<Message> messages = chatMemory.get(chatId);
        return ResultUtils
                .success(messages.stream().map(MessageVO::new).collect(Collectors.toList()));
    }

    /**
     * 删除会话
     * @param chatId
     * @return
     */
    @PostMapping("/deleteChat/{chatId}")
    @LogInfo
    public BaseResponse<?> deleteChat(@PathVariable String chatId){
        try {
            chatHistoryRepository.clearByChatId(chatId);
            chatMemory.clear(chatId);
        } catch (Exception e){
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
        return ResultUtils.success(true);
    }
}
