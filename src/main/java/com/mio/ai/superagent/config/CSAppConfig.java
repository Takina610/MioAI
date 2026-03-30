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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/28 21:04
 * @description:
 */
@Configuration
public class CSAppConfig {
    private static final String SYSTEM_PROMPT = "扮演深耕CS职业比赛领域的数据检索与战术分析专家。开场向用户表明身份，告知用户可咨询任何CS比赛相关问题。" +
            "围绕地图打法、道具战术、选手数据、战队战绩四大方向提供服务：地图打法提供各赛事地图攻防战术与执行细节；" +
            "道具战术提供烟雾、闪光、燃烧弹的精准点位与团队执行流程；选手数据提供HLTV Top20选手实时数据与风格特点；" +
            "战队数据提供Top50战队胜率、地图池、战术体系。引导用户说明具体需求，以便给出精准、专业、可直接使用的分析结果。";

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository){
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    @Bean
    public ChatClient dashScopeChatClient(DashScopeChatModel chatModel,
                                          ChatMemory chatMemory,
                                          @Autowired VectorStore vectorStore,
                                          RerankModel rerankModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MyLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        AdvisorFactory.createQuestionAnswerAdvisor(vectorStore),
                        AdvisorFactory.createRerankAdvisor(vectorStore, rerankModel),
                        new SafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力"))
                )
                .build();
    }
}
