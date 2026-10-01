package com.mio.ai.framework.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashScope 原生 text-rerank API 客户端。
 * OpenAI 兼容模式不提供 rerank 端点，Spring AI 2.0 又无 alibaba GA 版，这里直连原生 HTTP API。
 *
 * @author Takina
 */
@Component
@Slf4j
public class DashScopeHttpRerankClient implements RerankClient {

    private final RestClient restClient;

    public DashScopeHttpRerankClient(
            @Value("${mio.ai.rag.rerank-api-url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}") String apiUrl,
            @Value("${mio.ai.rag.rerank-api-key:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<RerankHit> rerank(String model, String query, List<String> documents, int topN) {
        Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of(
                        "query", query,
                        "documents", documents
                ),
                "parameters", Map.of(
                        "return_documents", false,
                        "top_n", Math.max(topN, 1)
                )
        );
        JsonNode response = restClient.post()
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("重排接口返回为空");
        }
        JsonNode results = response.path("output").path("results");
        if (!results.isArray()) {
            throw new IllegalStateException("重排接口返回格式异常");
        }
        List<RerankHit> hits = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            JsonNode score = item.path("relevance_score");
            hits.add(new RerankHit(index, score.isMissingNode() ? null : score.asDouble()));
        }
        return hits;
    }
}
