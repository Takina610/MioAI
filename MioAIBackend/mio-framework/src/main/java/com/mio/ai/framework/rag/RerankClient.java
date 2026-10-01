package com.mio.ai.framework.rag;

import java.util.List;

/**
 * 重排客户端抽象：隔离具体厂商 API，供 RAG 检索与重排 Advisor 复用。
 * 失败时实现抛异常，由调用方降级为向量检索原始顺序。
 */
public interface RerankClient {

    /**
     * 对候选文档重排序
     *
     * @param model     重排模型名
     * @param query     查询文本
     * @param documents 候选文档文本（顺序即原始顺序）
     * @param topN      返回前 N 条
     * @return 按相关性降序排列的命中（index 对应 documents 下标）
     */
    List<RerankHit> rerank(String model, String query, List<String> documents, int topN);

    /**
     * @param index 对应 documents 的下标
     * @param score 相关性得分
     */
    record RerankHit(int index, Double score) {
    }
}
