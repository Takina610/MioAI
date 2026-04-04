package com.mio.ai.superagent.app;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * @author: Takina
 * @date: 2026/4/4 19:49
 * @description:
 */

@Component
public class DefaultApp {
    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Resource(name = "defaultChatClient")
    private ChatClient chatClient;

    public Flux<String> doChat(ChatVO chatVO) {
        if (null != chatVO.getUserId()) {
            chatHistoryRepository.save(chatVO);
        }
        return chatClient
                .prompt()
                .user(chatVO.getMessage())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId()))
                .stream()
                .content();
    }
}
