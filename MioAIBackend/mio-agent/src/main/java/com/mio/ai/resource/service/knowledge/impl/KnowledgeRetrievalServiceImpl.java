package com.mio.ai.resource.service.knowledge.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.framework.rag.KnowledgeChunkQueryClient;
import com.mio.ai.framework.rag.KeywordExtractor;
import com.mio.ai.resource.model.entity.AgentKnowledge;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.framework.rag.KnowledgeRetrievalResult;
import com.mio.ai.framework.rag.RerankClient;
import com.mio.ai.framework.rag.RetrievalConfig;
import com.mio.ai.framework.rag.RetrievalOutcome;
import com.mio.ai.resource.service.agent.AgentKnowledgeService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.knowledge.KnowledgeRetrievalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 知识库检索服务实现
 * <p>检索隔离方案：向量库写入时已在 metadata 中携带 kbId/docId/fileName（见 KnowledgeBaseCreateServiceImpl），
 * 检索时用 filterExpression 限定 kbId 范围，从根源上杜绝"检索全库"导致的跨用户数据泄露。
 * <p>混合检索：向量召回（语义）+ 关键词召回（pgvector ILIKE，专有名词/精确术语），
 * 两路排名用 RRF（Reciprocal Rank Fusion, k=60）融合；关键词路失败自动降级纯向量。
 */
@Slf4j
@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    /**
     * 送入重排模型的最大候选数，防止长上下文导致重排耗时/费用过高
     */
    private static final int MAX_RERANK_CANDIDATES = 20;

    /**
     * RRF 融合常数：排名越靠前贡献越大，60 是论文推荐的经验值
     */
    private static final int RRF_K = 60;

    /**
     * 关键词召回的最大候选行数
     */
    private static final int MAX_KEYWORD_CANDIDATES = 50;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private com.mio.ai.framework.rag.EmbeddingThrottle embeddingThrottle;

    @Autowired
    private KnowledgeChunkQueryClient chunkQueryClient;

    @Resource
    private AgentKnowledgeService agentKnowledgeService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ObjectProvider<RerankClient> rerankClientProvider;

    /**
     * 重排总开关（全局）
     */
    @Value("${mio.ai.rag.rerank-enabled:false}")
    private boolean rerankEnabled;

    @Value("${mio.ai.rag.rerank-model:gte-rerank}")
    private String rerankModel;

    /**
     * 重排后保留的最大结果数（默认值，绑定级配置可覆盖）
     */
    @Value("${mio.ai.rag.rerank-top-n:5}")
    private int rerankTopN;

    @Override
    public List<KnowledgeRetrievalResult> retrieveForAgent(Long agentId, String query) {
        return retrieveForAgentDetailed(agentId, query).results();
    }

    @Override
    public RetrievalOutcome retrieveForAgentDetailed(Long agentId, String query) {
        if (agentId == null || StringUtils.isBlank(query)) {
            return emptyOutcome();
        }
        List<AgentKnowledge> bindings = agentKnowledgeService.getEnabledBindingsByAgentId(agentId);
        if (bindings.isEmpty()) {
            return emptyOutcome();
        }

        // 逐绑定按各自配置检索，再合并（每个知识库可以有自己的 topK/阈值/方式/重排开关）
        Map<String, KnowledgeRetrievalResult> merged = new LinkedHashMap<>();
        boolean anyRerank = false;
        boolean anyHybrid = false;
        int maxTopK = 0;
        int maxRerankTopN = 0;
        double minThreshold = 1.0;
        for (AgentKnowledge binding : bindings) {
            KnowledgeBase kb = knowledgeBaseService.getById(binding.getKbId());
            if (kb == null) {
                continue;
            }
            RetrievalConfig config = RetrievalConfig.fromJson(binding.getRetrievalConfig());
            anyRerank = anyRerank || config.isEnableRerank();
            anyHybrid = anyHybrid || config.isHybrid();
            maxTopK = Math.max(maxTopK, config.getTopK());
            maxRerankTopN = Math.max(maxRerankTopN,
                    config.isEnableRerank() ? config.getRerankTopN() : 0);
            minThreshold = Math.min(minThreshold, config.getThreshold());

            List<KnowledgeRetrievalResult> hits = searchWithConfig(List.of(kb.getId()), query, config);
            for (KnowledgeRetrievalResult hit : hits) {
                merged.putIfAbsent(hit.getChunkId(), hit);
            }
        }
        if (merged.isEmpty()) {
            return emptyOutcome();
        }

        List<KnowledgeRetrievalResult> results = new ArrayList<>(merged.values());
        results.sort(Comparator.comparing(KnowledgeRetrievalResult::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if (anyRerank && rerankEnabled) {
            int topN = maxRerankTopN > 0 ? maxRerankTopN : rerankTopN;
            results = rerank(query, results, Math.max(topN, Math.min(maxTopK, MAX_RERANK_CANDIDATES)));
        }
        String method = anyHybrid ? RetrievalConfig.METHOD_HYBRID : RetrievalConfig.METHOD_VECTOR;
        return new RetrievalOutcome(results, maxTopK, minThreshold >= 1.0
                ? RetrievalConfig.DEFAULT_THRESHOLD : minThreshold, method);
    }

    @Override
    public List<KnowledgeRetrievalResult> retrieveForPublic(String query) {
        return retrieveForPublicDetailed(query).results();
    }

    @Override
    public RetrievalOutcome retrieveForPublicDetailed(String query) {
        if (StringUtils.isBlank(query)) {
            return emptyOutcome();
        }
        List<KnowledgeBase> publicKbs = knowledgeBaseService.lambdaQuery()
                .eq(KnowledgeBase::getIsPublic, 1)
                .list();
        if (publicKbs.isEmpty()) {
            return emptyOutcome();
        }
        List<Long> kbIds = publicKbs.stream().map(KnowledgeBase::getId).toList();
        return retrieveDetailed(kbIds, query, RetrievalConfig.defaults());
    }

    @Override
    public List<KnowledgeRetrievalResult> retrieve(Collection<Long> kbIds, String query,
                                                   Integer topK, Double threshold, boolean rerank) {
        RetrievalConfig config = RetrievalConfig.defaults();
        config.setMethod(RetrievalConfig.METHOD_VECTOR);
        if (topK != null && topK > 0) {
            config.setTopK(Math.min(topK, 50));
        }
        if (threshold != null && threshold >= 0 && threshold <= 1) {
            config.setThreshold(threshold);
        }
        config.setEnableRerank(rerank);
        return retrieveDetailed(kbIds, query, config).results();
    }

    @Override
    public RetrievalOutcome retrieveDetailed(Collection<Long> kbIds, String query, RetrievalConfig config) {
        if (kbIds == null || kbIds.isEmpty() || StringUtils.isBlank(query) || config == null) {
            return emptyOutcome();
        }
        RetrievalConfig effective = RetrievalConfig.defaults();
        effective.setTopK(config.getTopK() > 0 ? Math.min(config.getTopK(), 50) : RetrievalConfig.DEFAULT_TOP_K);
        effective.setThreshold(config.getThreshold() >= 0 && config.getThreshold() <= 1
                ? config.getThreshold() : RetrievalConfig.DEFAULT_THRESHOLD);
        effective.setEnableRerank(config.isEnableRerank());
        effective.setMethod(config.isHybrid() ? RetrievalConfig.METHOD_HYBRID : RetrievalConfig.METHOD_VECTOR);
        effective.setRerankTopN(Math.max(1, Math.min(config.getRerankTopN(), 20)));

        List<KnowledgeRetrievalResult> hits = searchWithConfig(kbIds, query, effective);
        if (effective.isEnableRerank() && rerankEnabled) {
            hits = rerank(query, hits, effective.getRerankTopN());
        }
        return new RetrievalOutcome(hits, effective.getTopK(), effective.getThreshold(), effective.getMethod());
    }

    /**
     * 按配置执行单次检索：vector=纯向量；hybrid=向量+关键词 RRF 融合
     */
    private List<KnowledgeRetrievalResult> searchWithConfig(Collection<Long> kbIds, String query,
                                                            RetrievalConfig config) {
        if (!config.isHybrid()) {
            return searchVectorByKbIds(kbIds, query, config.getTopK(), config.getThreshold());
        }
        return searchHybrid(kbIds, query, config);
    }

    private List<KnowledgeRetrievalResult> searchVectorByKbIds(Collection<Long> kbIds, String query,
                                                               int topK, double threshold) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression =
                builder.in("kbId", kbIds.toArray(Long[]::new)).build();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression(filterExpression)
                .build();
        List<Document> documents = searchWithRetry(request);
        if (documents == null) {
            return List.of();
        }
        return documents.stream().map(doc -> {
            KnowledgeRetrievalResult result = toResult(doc);
            markMatchType(result, "vector");
            return result;
        }).toList();
    }

    /**
     * 混合检索：向量召回（topK 的 2 倍候选）+ 关键词召回，RRF 融合排序。
     * 分块得分语义：向量命中=余弦相似度；仅关键词命中=按命中率折算（0.4~0.99）；
     * 送重排时统一被重排分数覆盖
     */
    private List<KnowledgeRetrievalResult> searchHybrid(Collection<Long> kbIds, String query,
                                                        RetrievalConfig config) {
        int candidateK = Math.min(Math.max(config.getTopK() * 2, config.getTopK()), MAX_RERANK_CANDIDATES);
        List<KnowledgeRetrievalResult> vectorHits =
                searchVectorByKbIds(kbIds, query, candidateK, config.getThreshold());

        List<String> terms = KeywordExtractor.extract(query);
        if (terms.isEmpty()) {
            return truncate(vectorHits, config.getTopK());
        }
        List<KnowledgeChunkQueryClient.KeywordHit> keywordHits;
        try {
            keywordHits = chunkQueryClient.searchByKeywords(kbIds, terms, MAX_KEYWORD_CANDIDATES);
        } catch (Exception e) {
            log.warn("关键词召回失败，降级为纯向量检索: {}", e.getMessage());
            return truncate(vectorHits, config.getTopK());
        }
        if (keywordHits.isEmpty()) {
            return truncate(vectorHits, config.getTopK());
        }

        // 关键词行转结果：已在向量命中里的跳过（保留向量得分），其余按命中率折算得分
        Map<String, KnowledgeRetrievalResult> keywordOnly = new LinkedHashMap<>();
        for (KnowledgeChunkQueryClient.KeywordHit hit : keywordHits) {
            KnowledgeChunkQueryClient.ChunkRow row = hit.row();
            if (vectorHits.stream().anyMatch(v -> row.id() != null && row.id().equals(v.getChunkId()))) {
                continue;
            }
            double ratio = (double) hit.matchedTerms() / terms.size();
            KnowledgeRetrievalResult result = new KnowledgeRetrievalResult(
                    row.id(), row.content(),
                    Math.min(0.99, 0.4 + 0.6 * ratio),
                    row.kbId(), row.docId(), row.fileName(), new HashMap<>());
            markMatchType(result, "keyword");
            keywordOnly.put(row.id(), result);
        }
        if (keywordOnly.isEmpty()) {
            return truncate(vectorHits, config.getTopK());
        }

        // RRF 融合：向量排名 + 关键词排名（按命中词数降序）共同决定最终顺序
        List<KnowledgeChunkQueryClient.ChunkRow> keywordRows = keywordHits.stream()
                .sorted(Comparator.comparingInt(KnowledgeChunkQueryClient.KeywordHit::matchedTerms).reversed())
                .map(KnowledgeChunkQueryClient.KeywordHit::row)
                .toList();
        Map<String, Double> fusedScores = new HashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            fusedScores.merge(vectorHits.get(i).getChunkId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < keywordRows.size(); i++) {
            String id = keywordRows.get(i).id();
            if (id != null) {
                fusedScores.merge(id, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }

        Map<String, KnowledgeRetrievalResult> union = new LinkedHashMap<>();
        vectorHits.forEach(v -> union.put(v.getChunkId(), v));
        keywordOnly.forEach(union::putIfAbsent);
        List<KnowledgeRetrievalResult> fused = new ArrayList<>(union.values());
        fused.sort(Comparator.comparingDouble((KnowledgeRetrievalResult r) ->
                fusedScores.getOrDefault(r.getChunkId(), 0.0)).reversed());

        return truncate(fused, config.isEnableRerank()
                ? Math.min(Math.max(config.getTopK(), config.getRerankTopN()), MAX_RERANK_CANDIDATES)
                : config.getTopK());
    }

    private void markMatchType(KnowledgeRetrievalResult result, String matchType) {
        Map<String, Object> copied = new HashMap<>(result.getMetadata() != null ? result.getMetadata() : Map.of());
        copied.put("matchType", matchType);
        result.setMetadata(copied);
    }

    private <T> List<T> truncate(List<T> list, int limit) {
        return list.size() <= limit ? list : new ArrayList<>(list.subList(0, limit));
    }

    /**
     * 查询嵌入同样走嵌入接口、与向量化共享 RPM 限额（429 时会报错）：
     * 失败后先过全局节流队列（保证与上次嵌入请求间隔足够）再重试一次，仍失败再抛出
     */
    private List<Document> searchWithRetry(SearchRequest request) {
        try {
            return vectorStore.similaritySearch(request);
        } catch (Exception first) {
            log.warn("向量检索失败（多为嵌入请求限流），节流排队后重试一次: {}", first.getMessage());
            embeddingThrottle.awaitTurn();
            return vectorStore.similaritySearch(request);
        }
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
     * 调用重排模型对候选分块重排序（按原始下标精确回填）；失败时降级为检索原始顺序
     */
    private List<KnowledgeRetrievalResult> rerank(String query,
                                                  List<KnowledgeRetrievalResult> candidates,
                                                  int topN) {
        RerankClient rerankClient = rerankClientProvider.getIfAvailable();
        if (rerankClient == null || candidates.isEmpty()) {
            return candidates;
        }
        try {
            List<KnowledgeRetrievalResult> inputs = candidates.subList(0,
                    Math.min(candidates.size(), MAX_RERANK_CANDIDATES));
            List<String> documents = inputs.stream()
                    .map(r -> r.getText() == null ? "" : r.getText())
                    .toList();
            List<RerankClient.RerankHit> hits = rerankClient.rerank(rerankModel, query, documents,
                    Math.min(Math.max(topN, 1), inputs.size()));
            List<KnowledgeRetrievalResult> reranked = new ArrayList<>();
            for (RerankClient.RerankHit hit : hits) {
                if (hit.index() < 0 || hit.index() >= inputs.size()) {
                    continue;
                }
                KnowledgeRetrievalResult origin = inputs.get(hit.index());
                if (hit.score() != null) {
                    origin.setScore(hit.score());
                }
                markMatchType(origin, "rerank");
                reranked.add(origin);
            }
            log.info("Rerank 完成：候选 {} 条 → 返回 {} 条", inputs.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank 失败，降级为检索原始顺序: {}", e.getMessage());
            return candidates;
        }
    }

    private RetrievalOutcome emptyOutcome() {
        return new RetrievalOutcome(List.of(), RetrievalConfig.DEFAULT_TOP_K,
                RetrievalConfig.DEFAULT_THRESHOLD, RetrievalConfig.DEFAULT_METHOD);
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
