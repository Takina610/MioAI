package com.mio.ai.framework.rag;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 智能体-知识库绑定上的检索配置
 * <p>对应 agent_knowledge.retrieval_config（JSON），支持 camelCase 与 snake_case 两种键名：
 * {@code {"topK": 5, "threshold": 0.4, "enableRerank": false, "method": "hybrid", "rerankTopN": 5}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalConfig implements Serializable {

    /**
     * 检索返回的最大分块数
     */
    private int topK = DEFAULT_TOP_K;

    /**
     * 相似度阈值（0~1，越大越严格）
     */
    private double threshold = DEFAULT_THRESHOLD;

    /**
     * 该知识库检索结果是否参与重排
     */
    private boolean enableRerank = false;

    /**
     * 检索方式：vector=纯向量语义检索；hybrid=向量+关键词（pgvector ILIKE）RRF 融合，
     * 关键词侧负责专有名词/精确术语的召回
     */
    private String method = DEFAULT_METHOD;

    /**
     * 重排后保留的结果数（enableRerank=true 时生效）
     */
    private int rerankTopN = DEFAULT_RERANK_TOP_N;

    public static final int DEFAULT_TOP_K = 5;
    /**
     * mistral-embed 的余弦基线相似度很高（实测不相关跨主题查询也有 ~0.72，相关查询 ~0.84-0.86），
     * 旧的 0.4 阈值等于放行全部结果；0.7 可滤掉跨主题噪声。已存量的绑定级 threshold 不受影响
     */
    public static final double DEFAULT_THRESHOLD = 0.7;
    public static final String METHOD_VECTOR = "vector";
    public static final String METHOD_HYBRID = "hybrid";
    /**
     * 混合检索通常优于单路召回；关键词侧失败时自动降级为纯向量
     */
    public static final String DEFAULT_METHOD = METHOD_HYBRID;
    public static final int DEFAULT_RERANK_TOP_N = 5;

    public static RetrievalConfig defaults() {
        return new RetrievalConfig(DEFAULT_TOP_K, DEFAULT_THRESHOLD, false, DEFAULT_METHOD, DEFAULT_RERANK_TOP_N);
    }

    public boolean isHybrid() {
        return !METHOD_VECTOR.equalsIgnoreCase(method);
    }

    /**
     * 从 JSON 字符串解析，非法输入回退到默认值
     */
    public static RetrievalConfig fromJson(String json) {
        RetrievalConfig config = defaults();
        if (json == null || json.isBlank()) {
            return config;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            Integer topK = obj.getInt("topK", obj.getInt("top_k", null));
            if (topK != null && topK > 0) {
                config.setTopK(Math.min(topK, 50));
            }
            Double threshold = obj.getDouble("threshold", obj.getDouble("score_threshold",
                    obj.getDouble("scoreThreshold", null)));
            if (threshold != null && threshold >= 0 && threshold <= 1) {
                config.setThreshold(threshold);
            }
            Boolean enableRerank = obj.getBool("enableRerank", obj.getBool("enable_rerank", null));
            if (enableRerank != null) {
                config.setEnableRerank(enableRerank);
            }
            String method = obj.getStr("method", obj.getStr("retrieval_method", null));
            if (method != null && !method.isBlank()) {
                config.setMethod(METHOD_HYBRID.equalsIgnoreCase(method) ? METHOD_HYBRID : METHOD_VECTOR);
            }
            Integer rerankTopN = obj.getInt("rerankTopN", obj.getInt("rerank_top_n", null));
            if (rerankTopN != null) {
                config.setRerankTopN(Math.max(1, Math.min(rerankTopN, 20)));
            }
        } catch (Exception ignored) {
            // 配置格式错误时按默认值检索
        }
        return config;
    }
}
