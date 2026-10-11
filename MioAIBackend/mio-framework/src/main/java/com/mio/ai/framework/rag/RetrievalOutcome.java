package com.mio.ai.framework.rag;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 一次检索的完整结果与生效参数，供命中测试接口与检索日志使用，
 *               避免日志里记的是默认阈值而非实际生效值
 */
public record RetrievalOutcome(
        List<KnowledgeRetrievalResult> results,
        int effectiveTopK,
        double effectiveThreshold,
        String method
) {
}
