package com.mio.ai.customagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 检索配置解析纯单元测试（不依赖任何外部环境）
 */
class RetrievalConfigTest {

    @Test
    void nullOrBlankFallsBackToDefaults() {
        RetrievalConfig config = RetrievalConfig.fromJson(null);
        Assertions.assertEquals(RetrievalConfig.DEFAULT_TOP_K, config.getTopK());
        Assertions.assertEquals(RetrievalConfig.DEFAULT_THRESHOLD, config.getThreshold(), 1e-9);
        Assertions.assertFalse(config.isEnableRerank());

        Assertions.assertEquals(RetrievalConfig.DEFAULT_TOP_K,
                RetrievalConfig.fromJson("").getTopK());
        Assertions.assertEquals(RetrievalConfig.DEFAULT_TOP_K,
                RetrievalConfig.fromJson("   ").getTopK());
    }

    @Test
    void parsesCamelCaseKeys() {
        RetrievalConfig config = RetrievalConfig.fromJson(
                "{\"topK\": 8, \"threshold\": 0.55, \"enableRerank\": true}");
        Assertions.assertEquals(8, config.getTopK());
        Assertions.assertEquals(0.55, config.getThreshold(), 1e-9);
        Assertions.assertTrue(config.isEnableRerank());
    }

    @Test
    void parsesSnakeCaseKeys() {
        RetrievalConfig config = RetrievalConfig.fromJson(
                "{\"top_k\": 10, \"score_threshold\": 0.6, \"enable_rerank\": true}");
        Assertions.assertEquals(10, config.getTopK());
        Assertions.assertEquals(0.6, config.getThreshold(), 1e-9);
        Assertions.assertTrue(config.isEnableRerank());
    }

    @Test
    void invalidValuesFallBackPartially() {
        // 非法 topK/阈值被忽略，其余字段仍生效
        RetrievalConfig config = RetrievalConfig.fromJson(
                "{\"topK\": -3, \"threshold\": 2.5, \"enableRerank\": true}");
        Assertions.assertEquals(RetrievalConfig.DEFAULT_TOP_K, config.getTopK());
        Assertions.assertEquals(RetrievalConfig.DEFAULT_THRESHOLD, config.getThreshold(), 1e-9);
        Assertions.assertTrue(config.isEnableRerank());

        // 超大 topK 被限制到上限 50
        Assertions.assertEquals(50, RetrievalConfig.fromJson("{\"topK\": 999}").getTopK());
    }

    @Test
    void malformedJsonFallsBackToDefaults() {
        RetrievalConfig config = RetrievalConfig.fromJson("{not valid json");
        Assertions.assertEquals(RetrievalConfig.DEFAULT_TOP_K, config.getTopK());
        Assertions.assertEquals(RetrievalConfig.DEFAULT_THRESHOLD, config.getThreshold(), 1e-9);
        Assertions.assertFalse(config.isEnableRerank());
    }
}
