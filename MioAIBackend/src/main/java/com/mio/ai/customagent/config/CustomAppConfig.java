package com.mio.ai.customagent.config;

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
 * @date: 2026/4/8 9:12
 * @description:
 */

@Configuration
public class CustomAppConfig {
    /**
     * 自定义智能体的 ChatClient。
     * 知识库检索不再通过全局 QuestionAnswerAdvisor 执行（它会检索整个向量库，造成跨用户数据泄露），
     * 而是由 CustomApp 按智能体绑定的知识库（filterExpression 限定 kbId）检索后注入 system 提示词。
     */
    @Bean(name = "customChatClient")
    public ChatClient chatClient(OpenAiChatModel chatModel,
                                 ChatMemory jdbcChatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new ChineseSafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力")),
                        MessageChatMemoryAdvisor.builder(jdbcChatMemory).build()
                )
                .build();
    }
}
