package com.mio.ai.superagent.controller;

import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.superagent.agent.MioManus;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
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
     */
    @RequestMapping("/chat")
    public SseEmitter doChatWithManus(@RequestParam String chatId,
                                      @RequestParam Long agentId,
                                      @RequestParam String content,
                                      @RequestParam String token) {
        ChatVO chatVO = new ChatVO();
        chatVO.setChatId(chatId);
        chatVO.setMessage(content);
        chatVO.setAgentId(agentId);
        chatVO.setUserId(redisComponent.getUserId(token));
        chatHistoryRepository.save(chatVO);

        MioManus mioManus = new MioManus(commonTools, mioManusChatClient);
        return mioManus.runStream(content, chatVO.getChatId());
    }
}
