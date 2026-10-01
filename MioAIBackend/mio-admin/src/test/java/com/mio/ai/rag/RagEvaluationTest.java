package com.mio.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.framework.rag.KnowledgeRetrievalResult;
import com.mio.ai.customagent.service.knowledge.KnowledgeRetrievalService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RAG 检索消融实验（论文实验章节配套代码）。
 *
 * <p>实验内容：在同一评测集上对比不同检索配置的效果，指标为 Recall@k / docHit@k / MRR / 平均检索耗时。
 *
 * <p>运行前提（依赖真实环境）：
 * <ol>
 *   <li>MySQL / Redis(RedisStack) 已启动，后端配置可用（application.yml）；</li>
 *   <li>已创建一个知识库并上传 src/main/resources/rag 下的 4 篇 CS 文档完成向量化；</li>
 *   <li>设置环境变量 RAG_EVAL=true、RAG_EVAL_KB_ID=&lt;该知识库ID&gt;；
 *       若要做重排对比，另需设置 DASHSCOPE_API_KEY 并以 -Dmio.ai.rag.rerank-enabled=true 启动。</li>
 * </ol>
 *
 * <p>结果写入 target/rag-eval-results.md（Markdown 表格，可直接进论文）。
 * 分块大小（400/800/1200）通过 -Dmio.ai.rag.chunk-size=xxx 控制，每组实验需重新向量化后运行。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAG_EVAL", matches = "true")
class RagEvaluationTest {

    @Autowired
    private KnowledgeRetrievalService knowledgeRetrievalService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runEvaluation() throws IOException {
        Long kbId = Long.valueOf(System.getenv("RAG_EVAL_KB_ID"));
        EvalDataset dataset = loadDataset();

        // 消融变体：topK × 是否重排（rerank=false 为基线向量检索）
        record Variant(String name, int topK, boolean rerank) {}
        List<Variant> variants = List.of(
                new Variant("vector_top3", 3, false),
                new Variant("vector_top5", 5, false),
                new Variant("vector_top10", 10, false),
                new Variant("rerank_top5", 5, true)
        );

        StringBuilder report = new StringBuilder();
        report.append("# RAG 检索消融实验结果\n\n");
        report.append("- 评测集：").append(dataset.name()).append("（")
                .append(dataset.cases().size()).append(" 条用例）\n");
        report.append("- 知识库ID：").append(kbId).append("\n\n");
        report.append("| 变体 | Recall@k | docHit@k | MRR | 平均耗时(ms) |\n");
        report.append("|------|----------|----------|-----|--------------|\n");

        for (Variant variant : variants) {
            AtomicInteger recallHits = new AtomicInteger();
            AtomicInteger docHits = new AtomicInteger();
            List<Double> rrList = new ArrayList<>();
            List<Long> latencies = new ArrayList<>();

            for (EvalCase c : dataset.cases()) {
                long start = System.currentTimeMillis();
                List<KnowledgeRetrievalResult> results = knowledgeRetrievalService.retrieve(
                        List.of(kbId), c.question(), variant.topK(), 0.0, variant.rerank());
                latencies.add(System.currentTimeMillis() - start);

                int firstHitRank = 0;
                boolean recalled = false;
                boolean docHit = false;
                int rank = 0;
                for (KnowledgeRetrievalResult r : results) {
                    rank++;
                    String text = r.getText() == null ? "" : r.getText();
                    if (!recalled && containsAny(text, c.expectedKeywords())) {
                        recalled = true;
                        firstHitRank = rank;
                    }
                    if (!docHit && r.getFileName() != null && r.getFileName().contains(c.expectedDocKeyword())) {
                        docHit = true;
                    }
                }
                if (recalled) {
                    recallHits.incrementAndGet();
                    rrList.add(1.0 / firstHitRank);
                } else {
                    rrList.add(0.0);
                }
                if (docHit) {
                    docHits.incrementAndGet();
                }
            }

            int n = dataset.cases().size();
            double recall = 100.0 * recallHits.get() / n;
            double docHitRate = 100.0 * docHits.get() / n;
            double mrr = rrList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

            report.append(String.format("| %s | %.1f%% (%d/%d) | %.1f%% (%d/%d) | %.3f | %.0f |%n",
                    variant.name(), recall, recallHits.get(), n,
                    docHitRate, docHits.get(), n, mrr, avgLatency));
        }

        Path out = Path.of("target", "rag-eval-results.md");
        Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("结果已写入: " + out.toAbsolutePath());
        Assertions.assertTrue(dataset.cases().size() > 0);
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private EvalDataset loadDataset() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/rag-eval/eval-dataset.json")) {
            if (in == null) {
                throw new IllegalStateException("找不到评测集 /rag-eval/eval-dataset.json");
            }
            JsonNode root = objectMapper.readTree(in);
            String name = root.path("dataset").asText();
            List<EvalCase> cases = new ArrayList<>();
            for (JsonNode node : root.path("cases")) {
                List<String> keywords = new ArrayList<>();
                node.path("expectedKeywords").forEach(k -> keywords.add(k.asText()));
                cases.add(new EvalCase(
                        node.path("id").asText(),
                        node.path("question").asText(),
                        keywords,
                        node.path("expectedDocKeyword").asText()));
            }
            return new EvalDataset(name, cases);
        }
    }

    private record EvalDataset(String name, List<EvalCase> cases) {
    }

    private record EvalCase(String id, String question, List<String> expectedKeywords, String expectedDocKeyword) {
    }
}
