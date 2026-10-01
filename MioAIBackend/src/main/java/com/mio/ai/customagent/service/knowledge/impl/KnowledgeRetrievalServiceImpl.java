package com.mio.ai.customagent.service.knowledge.impl;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.customagent.model.entity.AgentKnowledge;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.rag.KnowledgeRetrievalResult;
import com.mio.ai.customagent.rag.RetrievalConfig;
import com.mio.ai.customagent.service.agent.AgentKnowledgeService;
import com.mio.ai.customagent.service.knowledge.KnowledgeBaseService;
import com.mio.ai.customagent.service.knowledge.KnowledgeRetrievalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 知识库检索服务实现
 * <p>检索隔离方案：向量库写入时已在 metadata 中携带 kbId/docId/fileName（见 KnowledgeBaseCreateServiceImpl），
 * 检索时用 filterExpression 限定 kbId 范围，从根源上杜绝"任何智能体检索全库"的问题。
 * 每个绑定的 topK/threshold 取自 agent_knowledge.retrieval_config，多知识库结果按得分合并去重。
 */
@Slf4j
@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    /**
     * 送入重排模型的最大候选数，防止长上下文导致重排耗时/费用过高
     */
    private static final int MAX_RERANK_CANDIDATES = 20;

    @Autowired
    private VectorStore vectorStore;

    @Resource
    private AgentKnowledgeService agentKnowledgeService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ObjectProvider<RerankModel> rerankModelProvider;

    /**
     * 重排总开关（全局），绑定上的 enableRerank 是细粒度开关，两者同时开启才生效
     */
    @Value("${mio.ai.rag.rerank-enabled:false}")
    private boolean rerankEnabled;

    @Value("${mio.ai.rag.rerank-model:gte-rerank}")
    private String rerankModel;

    /**
     * 重排后保留的最大结果数
     */
    @Value("${mio.ai.rag.rerank-top-n:5}")
    private int rerankTopN;

    @Override
    public List<KnowledgeRetrievalResult> retrieveForAgent(Long agentId, String query) {
        if (agentId == null || StringUtils.isBlank(query)) {
            return List.of();
        }
        List<AgentKnowledge> bindings = agentKnowledgeService.getEnabledBindingsByAgentId(agentId);
        if (bindings.isEmpty()) {
            return List.of();
        }

        // 逐绑定按各自配置检索，再合并（每个知识库可以有自己的 topK/阈值/重排开关）
        Map<String, KnowledgeRetrievalResult> merged = new LinkedHashMap<>();
        boolean anyRerank = false;
        int maxTopK = 0;
        for (AgentKnowledge binding : bindings) {
            KnowledgeBase kb = knowledgeBaseService.getById(binding.getKbId());
            if (kb == null) {
                continue;
            }
            RetrievalConfig config = RetrievalConfig.fromJson(binding.getRetrievalConfig());
            anyRerank = anyRerank || config.isEnableRerank();
            maxTopK = Math.max(maxTopK, config.getTopK());

            List<KnowledgeRetrievalResult> hits = searchByKbIds(List.of(kb.getId()), query,
                    config.getTopK(), config.getThreshold());
            for (KnowledgeRetrievalResult hit : hits) {
                merged.putIfAbsent(hit.getChunkId(), hit);
            }
        }
        if (merged.isEmpty()) {
            return List.of();
        }

        List<KnowledgeRetrievalResult> results = new ArrayList<>(merged.values());
        results.sort(Comparator.comparing(KnowledgeRetrievalResult::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if (anyRerank && rerankEnabled) {
            results = rerank(query, results, Math.max(maxTopK, rerankTopN));
        }
        return results;
    }

    @Override
    public List<KnowledgeRetrievalResult> retrieve(Collection<Long> kbIds, String query,
                                                   Integer topK, Double threshold, boolean rerank) {
        if (kbIds == null || kbIds.isEmpty() || StringUtils.isBlank(query)) {
            return List.of();
        }
        int effectiveTopK = topK != null && topK > 0 ? Math.min(topK, 50) : RetrievalConfig.DEFAULT_TOP_K;
        double effectiveThreshold = threshold != null && threshold >= 0 && threshold <= 1
                ? threshold : RetrievalConfig.DEFAULT_THRESHOLD;

        List<KnowledgeRetrievalResult> hits = searchByKbIds(kbIds, query, effectiveTopK, effectiveThreshold);
        if (rerank && rerankEnabled) {
            return rerank(query, hits, Math.max(effectiveTopK, rerankTopN));
        }
        return hits;
    }

    private List<KnowledgeRetrievalResult> searchByKbIds(Collection<Long> kbIds, String query,
                                                         int topK, double threshold) {
        Filter.Expression filterExpression = new FilterExpressionBuilder()
                .in("kbId", kbIds.toArray(Long[]::new))
                .build();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpression)
                .build();
        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null) {
            return List.of();
        }
        return documents.stream().map(this::toResult).toList();
    }

    private KnowledgeRetrievalResult toResult(Document doc) {
        Map<String, Object> metadata = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
        return new KnowledgeRetrievalResult(
                doc.getId(),
                doc.getText(),
                doc.getScore(),
                toLong(metadata.get("kbId")),
                toLong(metadata.get("docId")),
                metadata.get("fileName") != null ? String.valueOf(metadata.get("fileName")) : null,
                metadata
        );
    }

    /**
     * 调用 DashScope 重排模型对候选分块重排序；失败时降级为向量检索原始顺序
     */
    private List<KnowledgeRetrievalResult> rerank(String query,
                                                  List<KnowledgeRetrievalResult> candidates,
                                                  int topN) {
        RerankModel rerankModelBean = rerankModelProvider.getIfAvailable();
        if (rerankModelBean == null || candidates.isEmpty()) {
            return candidates;
        }
        try {
            List<KnowledgeRetrievalResult> inputs = candidates.subList(0,
                    Math.min(candidates.size(), MAX_RERANK_CANDIDATES));
            List<Document> documents = inputs.stream()
                    .map(r -> new Document(r.getChunkId(), r.getText(), Map.of()))
                    .toList();
            RerankRequest request = new RerankRequest(query, documents,
                    DashScopeRerankOptions.builder()
                            .withModel(rerankModel)
                            .withTopN(Math.min(topN, inputs.size()))
                            .withReturnDocuments(true)
                            .build());
            RerankResponse response = rerankModelBean.call(request);
            // DashScope 返回的结果不一定保留原文档 ID，按 ID 或正文双重匹配回原结果
            Map<String, KnowledgeRetrievalResult> byId = new HashMap<>();
            Map<String, KnowledgeRetrievalResult> byText = new HashMap<>();
            for (KnowledgeRetrievalResult r : inputs) {
                if (r.getChunkId() != null) {
                    byId.put(r.getChunkId(), r);
                }
                if (r.getText() != null) {
                    byText.put(r.getText().trim(), r);
                }
            }
            List<KnowledgeRetrievalResult> reranked = response.getResults().stream()
                    .map(dws -> {
                        Document doc = dws.getOutput();
                        if (doc == null) {
                            return null;
                        }
                        KnowledgeRetrievalResult origin = doc.getId() != null
                                ? byId.get(doc.getId()) : null;
                        if (origin == null && doc.getText() != null) {
                            origin = byText.get(doc.getText().trim());
                        }
                        if (origin != null && dws.getScore() != null) {
                            origin.setScore(dws.getScore());
                        }
                        return origin;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            log.info("Rerank 完成：候选 {} 条 → 返回 {} 条", inputs.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank 失败，降级为向量检索原始顺序: {}", e.getMessage());
            return candidates;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value != null ? Long.valueOf(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
