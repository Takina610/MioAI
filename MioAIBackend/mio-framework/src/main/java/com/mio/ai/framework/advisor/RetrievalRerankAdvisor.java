package com.mio.ai.framework.advisor;

import com.mio.ai.framework.rag.RerankClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检索 + 重排 Advisor：向量检索后经 {@link RerankClient} 重排，把结果渲染进用户消息。
 * 替代 spring-ai-alibaba 的 RetrievalRerankAdvisor（Spring AI 2.0 下无 alibaba GA 版）。
 *
 * @author Takina
 */
@Slf4j
public class RetrievalRerankAdvisor implements CallAdvisor {

    private final VectorStore vectorStore;
    private final RerankClient rerankClient;
    private final SearchRequest searchRequest;
    private final PromptTemplate promptTemplate;
    private final String rerankModel;
    private final int rerankTopN;

    public RetrievalRerankAdvisor(VectorStore vectorStore,
                                  RerankClient rerankClient,
                                  SearchRequest searchRequest,
                                  PromptTemplate promptTemplate,
                                  String rerankModel,
                                  int rerankTopN) {
        this.vectorStore = vectorStore;
        this.rerankClient = rerankClient;
        this.searchRequest = searchRequest;
        this.promptTemplate = promptTemplate;
        this.rerankModel = rerankModel;
        this.rerankTopN = rerankTopN;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        UserMessage userMessage = request.prompt().getUserMessage();
        String query = userMessage != null ? userMessage.getText() : "";

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(searchRequest.getTopK())
                .similarityThreshold(searchRequest.getSimilarityThreshold())
                .build());
        List<Document> processed = rerank(query, documents);

        String context = processed.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));

        String rendered = promptTemplate.render(Map.of(
                "query", query,
                "question_answer_context", context
        ));
        ChatClientRequest mutated = request.mutate()
                .prompt(request.prompt().augmentUserMessage(userMsg -> new UserMessage(rendered)))
                .build();
        return chain.nextCall(mutated);
    }

    /**
     * 重排候选文档；失败降级为向量检索原始顺序
     */
    private List<Document> rerank(String query, List<Document> documents) {
        if (rerankClient == null || documents == null || documents.isEmpty()) {
            return documents;
        }
        try {
            List<String> texts = documents.stream().map(Document::getText).toList();
            List<RerankClient.RerankHit> hits = rerankClient.rerank(rerankModel, query, texts,
                    Math.min(rerankTopN, documents.size()));
            List<Document> reranked = new ArrayList<>();
            for (RerankClient.RerankHit hit : hits) {
                if (hit.index() < 0 || hit.index() >= documents.size()) {
                    continue;
                }
                reranked.add(documents.get(hit.index()));
            }
            log.info("Rerank Advisor 完成：候选 {} 条 → 返回 {} 条", documents.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank Advisor 失败，降级为向量检索原始顺序: {}", e.getMessage());
            return documents;
        }
    }
}
