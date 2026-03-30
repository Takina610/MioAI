package com.mio.ai.superagent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
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

    private final ChatClient chatClient;

    public CSApp(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Resource
    private ToolCallback[] allTools;

    public Flux<String> doChat(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)
                .stream()
                .content();
    }
}
