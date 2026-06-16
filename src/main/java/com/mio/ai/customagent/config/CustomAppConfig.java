package com.mio.ai.customagent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.model.RerankModel;
import com.mio.ai.superagent.advisor.AdvisorFactory;
import com.mio.ai.superagent.advisor.ChineseSafeGuardAdvisor;
import com.mio.ai.superagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.VectorStore;
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
    @Bean(name = "customChatClient")
    public ChatClient dashScopeChatClient(DashScopeChatModel chatModel,
                                          ChatMemory jdbcChatMemory,
                                          VectorStore vectorStore,
                                          RerankModel rerankModel) {
        PromptTemplate promptTemplate = new PromptTemplate("""
                【用户问题信息】
                {query}
                【上下文信息】
                {question_answer_context}
                """);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new ChineseSafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力")),
                        MessageChatMemoryAdvisor.builder(jdbcChatMemory).build(),
                        AdvisorFactory.createQuestionAnswerAdvisor(vectorStore, promptTemplate)
//                        AdvisorFactory.createRerankAdvisor(vectorStore, rerankModel)
                )
                .build();
    }
}
