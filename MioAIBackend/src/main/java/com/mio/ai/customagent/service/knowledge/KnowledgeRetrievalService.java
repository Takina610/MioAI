package com.mio.ai.customagent.service.knowledge;

import com.mio.ai.customagent.rag.KnowledgeRetrievalResult;

import java.util.Collection;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 知识库检索服务
 * <p>核心职责：将检索范围严格限定在智能体绑定的知识库内（filterExpression），
 * 而不是像此前那样检索整个向量库，避免跨用户、跨知识库的数据泄露。
 * 同时让 agent_knowledge.retrieval_config（topK/threshold/enableRerank）真正生效。
 */
public interface KnowledgeRetrievalService {

    /**
     * 按智能体绑定的知识库及其各自的检索配置进行检索
     *
     * @param agentId 智能体ID
     * @param query   用户查询
     * @return 按相似度降序的命中分块（多知识库合并去重后）
     */
    List<KnowledgeRetrievalResult> retrieveForAgent(Long agentId, String query);

    /**
     * 在指定知识库集合内检索（用于命中测试等场景，调用方需自行完成权限校验）
     *
     * @param kbIds     允许检索的知识库ID集合
     * @param query     查询内容
     * @param topK      返回数量上限
     * @param threshold 相似度阈值
     * @param rerank    是否启用重排
     */
    List<KnowledgeRetrievalResult> retrieve(Collection<Long> kbIds, String query,
                                            Integer topK, Double threshold, boolean rerank);
}
