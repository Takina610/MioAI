package com.mio.ai.superagent.app;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.customagent.model.entity.AgentUsageLog;
import com.mio.ai.customagent.model.entity.RagRetrievalLog;
import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.customagent.service.log.RagRetrievalLogService;
import com.mio.ai.superagent.model.dto.SseChunk;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Resource(name = "csAppTools")
    ToolCallback[] csAppTools;

    @Resource(name = "csAppChatClient")
    private ChatClient chatClient;

    @Autowired
    private AgentUsageLogService agentUsageLogService;

    @Autowired
    private RagRetrievalLogService ragRetrievalLogService;

    @Autowired
    private VectorStore vectorStore;

    public Flux<SseChunk> doChat(ChatVO chatVO) {
        chatHistoryRepository.save(chatVO);

        logRagRetrieval(chatVO);

        long startTime = System.currentTimeMillis();
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(chatVO.getAgentId());
        usageLog.setUserId(chatVO.getUserId());
        usageLog.setConversationId(parseConversationId(chatVO.getChatId()));
        usageLog.setStatus(1);
        usageLog.setCreateTime(new Date());

        AtomicInteger inputTokens = new AtomicInteger();

        AtomicInteger outputTokens = new AtomicInteger();

        return chatClient
                .prompt()
                .user(chatVO.getMessage())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId()))
                .toolCallbacks(csAppTools)
                .stream()
                .chatResponse()
                .doOnNext(response -> extractUsage(response, inputTokens, outputTokens))
                // Spring AI 2.0 流式末尾会推一条仅含 usage 的响应（getResult() 为 null），必须判空
                .<SseChunk>handle((response, sink) -> {
                    SseChunk chunk = toChunk(response);
                    if (chunk != null) {
                        sink.next(chunk);
                    }
                })
                .concatWith(Mono.defer(() -> Mono.just(SseChunk.usage(
                        inputTokens.get(), outputTokens.get(),
                        System.currentTimeMillis() - startTime))))
                .doOnTerminate(() -> {
                    usageLog.setInputTokens(inputTokens.get());
                    usageLog.setOutputTokens(outputTokens.get());
                    usageLog.setResponseTime((int) (System.currentTimeMillis() - startTime));
                    agentUsageLogService.logUsage(usageLog);
                })
                .doOnError(error -> {
                    usageLog.setStatus(0);
                    usageLog.setErrorMsg(error.getMessage());
                });
    }

    /** 推理模型的思考增量归 thinking，其余归 answer 正文 */
    private SseChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        var output = response.getResult().getOutput();
        Object reasoning = output.getMetadata().get("reasoningContent");
        if (reasoning instanceof String reasoningText && !reasoningText.isEmpty()) {
            return SseChunk.delta("thinking", reasoningText);
        }
        String text = output.getText();
        if (StrUtil.isEmpty(text)) {
            return null;
        }
        return SseChunk.delta("answer", text);
    }

    private Long parseConversationId(String chatId) {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void extractUsage(ChatResponse response, AtomicInteger inputTokens, AtomicInteger outputTokens) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            inputTokens.set(usage.getPromptTokens().intValue());
        }
        if (usage.getCompletionTokens() != null) {
            outputTokens.set(usage.getCompletionTokens().intValue());
        }
    }

    private void logRagRetrieval(ChatVO chatVO) {
        long startTime = System.currentTimeMillis();
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(chatVO.getMessage())
                    .topK(5)
                    .similarityThreshold(0.4)
                    .build();
            List<Document> documents = vectorStore.similaritySearch(request);

            List<Map<String, Object>> chunks = documents.stream()
                    .map(doc -> Map.of(
                            "content", doc.getText(),
                            "metadata", doc.getMetadata()
                    ))
                    .toList();

            Long kbId = documents.isEmpty() ? null : extractKbId(documents.get(0));
            if (kbId == null) {
                kbId = 0L;
            }

            RagRetrievalLog retrievalLog = new RagRetrievalLog();
            retrievalLog.setAgentId(chatVO.getAgentId());
            retrievalLog.setUserId(chatVO.getUserId());
            retrievalLog.setKbId(kbId);
            retrievalLog.setQuery(chatVO.getMessage());
            retrievalLog.setRetrievedChunks(JacksonUtil.writeValueAsString(chunks));
            retrievalLog.setTopK(5);
            retrievalLog.setScoreThreshold(0.4f);
            retrievalLog.setResponseTime((int) (System.currentTimeMillis() - startTime));
            retrievalLog.setCreateTime(new Date());
            ragRetrievalLogService.logRetrieval(retrievalLog);
        } catch (Exception e) {
            log.error("记录RAG检索日志失败", e);
        }
    }

    private Long extractKbId(Document document) {
        Object kbId = document.getMetadata().get("kbId");
        if (kbId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(kbId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
