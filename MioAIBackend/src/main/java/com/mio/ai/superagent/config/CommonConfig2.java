package com.mio.ai.superagent.config;

import com.mio.ai.superagent.advisor.ChineseSafeGuardAdvisor;
import com.mio.ai.superagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/4 19:35
 * @description:
 */

@Configuration(enforceUniqueMethods = false)
public class CommonConfig2 {
    @Bean(name = "summaryChatClient")
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new MyLoggerAdvisor()
                )
                .build();
    }

    @Bean(name = "defaultChatClient")
    public ChatClient chatClient(OpenAiChatModel chatModel,
                                 ChatMemory jdbcChatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new ChineseSafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力"))
                )
                .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory jdbcChatMemory) {
        return MessageChatMemoryAdvisor.builder(jdbcChatMemory).build();
    }
}
