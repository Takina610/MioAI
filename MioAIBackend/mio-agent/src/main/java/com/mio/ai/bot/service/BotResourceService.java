package com.mio.ai.bot.service;

import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.framework.rag.KnowledgeRetrievalResult;
import com.mio.ai.resource.model.entity.RagRetrievalLog;
import com.mio.ai.resource.service.knowledge.KnowledgeRetrievalService;
import com.mio.ai.resource.service.log.RagRetrievalLogService;
import com.mio.ai.resource.service.mcp.McpClientManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MioBot 的动态资源装配：公共 MCP 工具池 + 公共知识库检索上下文。
 * <p>MioBot 是全站共享的唯一智能体，不维护私有绑定——
 * 公开的 MCP 工具（is_public=1）进入其工具池，公开的知识库进入检索范围。
 */
@Slf4j
@Service
public class BotResourceService {

    /**
     * 注入提示词的检索单块最大长度，避免上下文被单个分块撑爆
     */
    private static final int MAX_CHUNK_TEXT_LENGTH = 1500;

    /**
     * 注入提示词的最大命中块数
     */
    private static final int MAX_CONTEXT_CHUNKS = 8;

    private final McpClientManagerService mcpClientManagerService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final RagRetrievalLogService ragRetrievalLogService;

    public BotResourceService(McpClientManagerService mcpClientManagerService,
                              KnowledgeRetrievalService knowledgeRetrievalService,
                              RagRetrievalLogService ragRetrievalLogService) {
        this.mcpClientManagerService = mcpClientManagerService;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.ragRetrievalLogService = ragRetrievalLogService;
    }

    /**
     * 获取公共 MCP 工具回调；初始化失败时返回空数组（MioBot 仍可用内置工具工作）
     */
    public ToolCallback[] getPublicMcpToolCallbacks() {
        try {
            return mcpClientManagerService.getPublicMcpToolCallbacks();
        } catch (Exception e) {
            log.error("装配公共 MCP 工具失败，本轮仅使用内置工具", e);
            return new ToolCallback[0];
        }
    }

    /**
     * 在公共知识库中检索并拼装可注入系统提示词的上下文；同时落 rag_retrieval_log
     */
    public String buildKnowledgeContext(Long userId, String query) {
        long startTime = System.currentTimeMillis();
        List<KnowledgeRetrievalResult> results;
        try {
            results = knowledgeRetrievalService.retrieveForPublic(query);
        } catch (Exception e) {
            log.error("公共知识库检索失败", e);
            return "";
        }
        if (results.isEmpty()) {
            return "";
        }
        logRetrieval(userId, query, results, startTime);
        return renderContext(results);
    }

    private void logRetrieval(Long userId, String query,
                              List<KnowledgeRetrievalResult> results, long startTime) {
        try {
            List<Map<String, Object>> chunks = results.stream()
                    .map(r -> Map.<String, Object>of(
                            "chunkId", r.getChunkId() == null ? "" : r.getChunkId(),
                            "content", r.getText() == null ? "" : r.getText(),
                            "score", r.getScore() == null ? 0.0 : r.getScore(),
                            "kbId", r.getKbId() == null ? 0L : r.getKbId(),
                            "fileName", r.getFileName() == null ? "" : r.getFileName()
                    ))
                    .toList();

            RagRetrievalLog retrievalLog = new RagRetrievalLog();
            retrievalLog.setAgentId(1L);
            retrievalLog.setUserId(userId);
            retrievalLog.setKbId(results.get(0).getKbId() != null ? results.get(0).getKbId() : 0L);
            retrievalLog.setQuery(query);
            retrievalLog.setRetrievedChunks(JacksonUtil.writeValueAsString(chunks));
            retrievalLog.setTopK(results.size());
            retrievalLog.setScoreThreshold(0.4f);
            retrievalLog.setResponseTime((int) (System.currentTimeMillis() - startTime));
            retrievalLog.setCreateTime(new Date());
            ragRetrievalLogService.logRetrieval(retrievalLog);
        } catch (Exception e) {
            log.warn("记录RAG检索日志失败: {}", e.getMessage());
        }
    }

    private String renderContext(List<KnowledgeRetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("【知识库检索结果】\n");
        sb.append("以下是与用户问题相关的知识库片段，回答时优先依据这些内容；若与问题无关，请忽略并按你自己的知识回答：\n\n");
        int count = 0;
        for (KnowledgeRetrievalResult r : results) {
            if (count >= MAX_CONTEXT_CHUNKS) {
                break;
            }
            String text = r.getText() == null ? "" : r.getText();
            if (text.length() > MAX_CHUNK_TEXT_LENGTH) {
                text = text.substring(0, MAX_CHUNK_TEXT_LENGTH) + "...";
            }
            sb.append("[片段").append(count + 1);
            if (r.getFileName() != null && !r.getFileName().isBlank()) {
                sb.append(" | 来源: ").append(r.getFileName());
            }
            if (r.getScore() != null) {
                sb.append(" | 相似度: ").append(String.format("%.3f", r.getScore()));
            }
            sb.append("]\n").append(text).append("\n\n");
            count++;
        }
        return sb.toString().stripTrailing();
    }
}
