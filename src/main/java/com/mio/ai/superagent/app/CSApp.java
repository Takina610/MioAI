package com.mio.ai.superagent.app;

import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * @author: Takina
 * @date: 2026/3/28 21:03
 * @description:
 */

@Component
@Slf4j
public class CSApp {

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    private final ChatClient chatClient;

    public CSApp(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Flux<String> doChat(ChatVO chatVO) {
        chatHistoryRepository.save(chatVO);
        return chatClient
                .prompt()
                .user(chatVO.getMessage())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId()))
                .stream()
                .content();
    }
}
