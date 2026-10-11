package com.mio.ai.resource.service.knowledge;

import com.mio.ai.framework.rag.KnowledgeRetrievalResult;
import com.mio.ai.framework.rag.RetrievalConfig;
import com.mio.ai.framework.rag.RetrievalOutcome;

import java.util.Collection;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 知识库检索服务
 * <p>核心职责：将检索范围严格限定在允许的知识库集合内（filterExpression），
 * 而不是检索整个向量库，避免跨用户、跨知识库的数据泄露。
 */
public interface KnowledgeRetrievalService {

    /**
     * 按智能体绑定的知识库及其各自的检索配置进行检索（自定义智能体的检索范围）
     *
     * @param agentId 智能体ID
     * @param query   用户查询
     * @return 按相似度降序的命中分块（多知识库合并去重后）
     */
    List<KnowledgeRetrievalResult> retrieveForAgent(Long agentId, String query);

    /**
     * 同 {@link #retrieveForAgent}，但返回实际生效的检索参数（供日志/展示）
     */
    RetrievalOutcome retrieveForAgentDetailed(Long agentId, String query);

    /**
     * 在全部公共知识库中检索（MioBot 的默认检索范围）
     *
     * @param query 用户查询
     * @return 按相似度降序的命中分块
     */
    List<KnowledgeRetrievalResult> retrieveForPublic(String query);

    /**
     * 同 {@link #retrieveForPublic}，但返回实际生效的检索参数（供日志/展示）
     */
    RetrievalOutcome retrieveForPublicDetailed(String query);

    /**
     * 在指定知识库集合内检索（用于命中测试等场景，调用方需自行完成权限校验）。
     * 兼容旧签名：纯向量检索
     */
    List<KnowledgeRetrievalResult> retrieve(Collection<Long> kbIds, String query,
                                            Integer topK, Double threshold, boolean rerank);

    /**
     * 在指定知识库集合内按完整配置检索（混合检索/重排数量等），调用方需自行完成权限校验
     */
    RetrievalOutcome retrieveDetailed(Collection<Long> kbIds, String query, RetrievalConfig config);
}
