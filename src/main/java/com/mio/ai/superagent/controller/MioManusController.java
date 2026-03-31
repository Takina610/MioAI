package com.mio.ai.superagent.controller;

import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.superagent.agent.MioManus;
import com.mio.ai.superagent.model.dto.ChatMessageRequest;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @author: Takina
 * @date: 2026/3/31 19:17
 * @description:
 */

@RequestMapping("/mio")
@RestController
public class MioManusController {
    @Resource
    private ToolCallback[] commonTools;

    @Resource
    private ChatClient mioManusChatClient;

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    RedisComponent redisComponent;

    /**
     * 流式调用 Manus 超级智能体
     *
     * @param chatMessageRequest
     * @return
     */
    @PostMapping("/chat")
    public SseEmitter doChatWithManus(@RequestBody ChatMessageRequest chatMessageRequest, HttpServletRequest request) {
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatMessageRequest.getChatId());
        chatVO.setMessage(chatMessageRequest.getContent());
        chatVO.setAgentId(chatMessageRequest.getAgentId());
        chatVO.setUserId(redisComponent.getUserId(request.getHeader("token")));
        chatHistoryRepository.save(chatVO);

        MioManus mioManus = new MioManus(commonTools, mioManusChatClient);
        return mioManus.runStream(chatMessageRequest.getContent());
    }
}
