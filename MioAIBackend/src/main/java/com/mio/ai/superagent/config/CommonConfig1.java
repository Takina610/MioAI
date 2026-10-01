package com.mio.ai.superagent.config;

import com.mio.ai.customagent.rag.RerankClient;
import com.mio.ai.superagent.advisor.ChineseSafeGuardAdvisor;
import com.mio.ai.superagent.advisor.AdvisorFactory;
import com.mio.ai.superagent.memory.SummarizingChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/28 21:04
 * @description:
 */
@Configuration(enforceUniqueMethods = false)
public class CommonConfig1 {

    @Value("${mio.ai.memory.summary-enabled:true}")
    private boolean memorySummaryEnabled;

    @Value("${mio.ai.memory.summary-threshold:80}")
    private int memorySummaryThreshold;

    @Value("${mio.ai.memory.keep-recent-messages:20}")
    private int memoryKeepRecentMessages;

    @Value("${mio.ai.rag.rerank-model:gte-rerank}")
    private String rerankModel;

    @Value("${mio.ai.rag.rerank-top-n:5}")
    private int rerankTopN;

    /**
     * 长会话记忆：开启摘要后，超过阈值的历史会被压缩成摘要注入上下文（见 SummarizingChatMemory）；
     * 关闭时行为等同原来的固定 300 条窗口
     */
    @Bean(name = "jdbcChatMemory")
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository,
                                 @Qualifier("summaryChatClient") ChatClient summaryChatClient) {
        MessageWindowChatMemory windowMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(300)
                .build();
        if (memorySummaryEnabled) {
            return new SummarizingChatMemory(windowMemory, summaryChatClient,
                    memorySummaryThreshold, memoryKeepRecentMessages);
        }
        return windowMemory;
    }

    @Bean(name = "csAppChatClient")
    public ChatClient chatClient(OpenAiChatModel chatModel,
                                          ChatMemory jdbcChatMemory,
                                          VectorStore vectorStore,
                                          RerankClient rerankClient,
                                          @Value("${mio.ai.rag.rerank-enabled:false}") boolean rerankEnabled,
                                          ToolCallbackProvider toolCallbackProvider
    ) {
        PromptTemplate promptTemplate = new PromptTemplate("""
                【用户问题信息】
                {query}
                【上下文信息】
                {question_answer_context}

                回答请严格遵循以下规则：
                1. 若上下文包含与用户问题直接相关的地图打法、道具点位、战术执行、选手数据、战队信息，通过语义理解匹配后给出专业回答。
                2. 如果上下文无相关内容，结合CS职业赛事知识，给出准确、实战可用、专业的分析。
                3. 回答必须精准、简洁、专业，不编造选手数据、战队战绩、战术点位。
                4. 回复直接给出答案，不使用“根据上下文”“根据信息”等冗余表述。
                5. 语气保持专业、清晰、干练，符合职业教练/数据分析师风格，便于用户直接用于实战。
                6. 若用户问题与CS无关，礼貌告知仅提供CS比赛相关咨询服务。

                【强制输出格式 - 重要】
                7. 所有回答**必须使用标准 Markdown 格式排版**，像教程一样清晰易读。
                8. 重点内容用 **加粗** 突出；分点说明使用 `-` 列表；战术步骤使用 `1. 2. 3.` 有序列表。
                9. 地图名称、道具名称、选手名、战术术语可使用 `标记` 突出显示。
                10. 排版必须美观、层级分明，不要使用任何无格式纯文本输出。
                """);

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem("你是专业的CS比赛数据检索与战术分析大师，精通所有职业赛事地图打法、道具战术、HLTV选手数据与战队体系。" +
                        "开场表明身份，为用户提供地图攻防战术、道具投掷点位、选手数据查询、战队实力分析、赛事解读等服务。" +
                        "回答精准、专业、可直接用于实战与训练，引导用户说明具体地图、选手或战队需求，给出最专业的分析结论。")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new ChineseSafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力")),
                        MessageChatMemoryAdvisor.builder(jdbcChatMemory).build(),
                        // 重排开关：开启后由 RetrievalRerankAdvisor 检索 + gte-rerank 重排（消融实验对比项）；
                        // 关闭时使用普通 QuestionAnswerAdvisor 向量检索
                        rerankEnabled
                                ? AdvisorFactory.createRerankAdvisor(vectorStore, rerankClient, rerankModel, rerankTopN)
                                : AdvisorFactory.createQuestionAnswerAdvisor(vectorStore, promptTemplate)
                )
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks());
        return builder.build();
    }

    @Bean(name = "mioManusChatClient")
    public ChatClient chatClient(OpenAiChatModel chatModel,
                                          ChatMemory jdbcChatMemory,
                                          ToolCallbackProvider toolCallbackProvider) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new ChineseSafeGuardAdvisor(List.of("公务员", "政府", "政治", "暴力")),
                        MessageChatMemoryAdvisor.builder(jdbcChatMemory).build()
                )
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
    }
}
