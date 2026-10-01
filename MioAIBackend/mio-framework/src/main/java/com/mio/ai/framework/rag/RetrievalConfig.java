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
 * {@code {"topK": 5, "threshold": 0.4, "enableRerank": false}}
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

    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_THRESHOLD = 0.4;

    public static RetrievalConfig defaults() {
        return new RetrievalConfig(DEFAULT_TOP_K, DEFAULT_THRESHOLD, false);
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
        } catch (Exception ignored) {
            // 配置格式错误时按默认值检索
        }
        return config;
    }
}
