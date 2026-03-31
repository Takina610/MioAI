package com.mio.ai.superagent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.model.RerankModel;
import com.mio.ai.superagent.advisor.MyLoggerAdvisor;
import com.mio.ai.superagent.rag.AdvisorFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/28 21:04
 * @description:
 */
@Configuration(enforceUniqueMethods = false)
public class CommonConfig {
    private static final String SYSTEM_PROMPT = "你是专业的CS比赛数据检索与战术分析大师，精通所有职业赛事地图打法、道具战术、HLTV选手数据与战队体系。" +
            "开场表明身份，为用户提供地图攻防战术、道具投掷点位、选手数据查询、战队实力分析、赛事解读等服务。" +
            "回答精准、专业、可直接用于实战与训练，引导用户说明具体地图、选手或战队需求，给出最专业的分析结论。";

    @Bean(name = "jdbcChatMemory")
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository){
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(30)
                .build();
    }

    @Bean(name = "csAppChatClient")
    public ChatClient dashScopeChatClient(DashScopeChatModel chatModel,
                                          ChatMemory jdbcChatMemory,
                                          VectorStore vectorStore,
                                          RerankModel rerankModel
    ) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MyLoggerAdvisor(),
                        new SafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力")),
                        MessageChatMemoryAdvisor.builder(jdbcChatMemory).build(),
                        AdvisorFactory.createQuestionAnswerAdvisor(vectorStore),
                        AdvisorFactory.createRerankAdvisor(vectorStore, rerankModel)
                )
                .build();
    }

    @Bean(name = "mioManusChatClient")
    public ChatClient dashScopeChatClient(DashScopeChatModel chatModel,
                                          ChatMemory jdbcChatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new MyLoggerAdvisor(),
                        new SafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力")),
                        MessageChatMemoryAdvisor.builder(jdbcChatMemory).build()
                )
                .build();
    }
}
