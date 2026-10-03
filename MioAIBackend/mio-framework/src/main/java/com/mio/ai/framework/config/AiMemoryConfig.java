package com.mio.ai.framework.config;

import com.mio.ai.framework.advisor.MyLoggerAdvisor;
import com.mio.ai.framework.memory.SummarizingChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 基础装配：会话记忆与标题摘要客户端。
 * <p>MioBot 的 Agent 循环直连 ChatModel（工具手动执行），不经 ChatClient，
 * 因此这里只保留 jdbcChatMemory 与 summaryChatClient 两个 Bean。
 */
@Configuration(enforceUniqueMethods = false)
public class AiMemoryConfig {

    @Value("${mio.ai.memory.summary-enabled:true}")
    private boolean memorySummaryEnabled;

    @Value("${mio.ai.memory.summary-threshold:80}")
    private int memorySummaryThreshold;

    @Value("${mio.ai.memory.keep-recent-messages:20}")
    private int memoryKeepRecentMessages;

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

    @Bean(name = "summaryChatClient")
    public ChatClient summaryChatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
    }
}
