package com.mio.ai.customagent.app;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.model.entity.AgentUsageLog;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.vo.agent.AgentVO;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.customagent.model.entity.RagRetrievalLog;
import com.mio.ai.customagent.rag.KnowledgeRetrievalResult;
import com.mio.ai.customagent.service.agent.AgentService;
import com.mio.ai.customagent.service.knowledge.KnowledgeRetrievalService;
import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.customagent.service.log.RagRetrievalLogService;
import com.mio.ai.customagent.service.mcp.McpClientManagerService;
import com.mio.ai.customagent.service.security.AccessGuardService;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: Takina
 * @date: 2026/4/8 9:10
 * @description: 自定义智能体对话入口
 * <p>RAG 检索范围 = 智能体绑定的知识库（agent_knowledge），命中内容注入 system 提示词，
 * 并写入 rag_retrieval_log 供统计分析。
 */
@Component
@Slf4j
public class CustomApp {

    /**
     * 注入提示词的检索单块最大长度，避免上下文被单个分块撑爆
     */
    private static final int MAX_CHUNK_TEXT_LENGTH = 1500;

    /**
     * 注入提示词的最大命中块数
     */
    private static final int MAX_CONTEXT_CHUNKS = 8;

    @Resource(name = "customChatClient")
    ChatClient chatClient;

    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Autowired
    McpClientManagerService mcpClientManagerService;

    @Autowired
    AgentService agentService;

    @Autowired
    private AgentUsageLogService agentUsageLogService;

    @Autowired
    private RagRetrievalLogService ragRetrievalLogService;

    @Autowired
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Autowired
    private AccessGuardService accessGuardService;

    public Flux<String> doChat(ChatVO chatVO) {
        // 校验当前用户是否有权使用该智能体（所有者/内置/公开已发布）
        accessGuardService.checkAgentUsable(chatVO.getAgentId(), chatVO.getUserId());
        AgentVO agent = agentService.getAgentById(chatVO.getAgentId());
        if (null == agent) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体不存在");
        }

        List<McpTool> mcpTools = mcpClientManagerService.getAgentMcpTools(chatVO.getAgentId());
        ToolCallback[] toolCallbacks = mcpClientManagerService.initMcpToolCallbacks(mcpTools);

        chatHistoryRepository.save(chatVO);

        // 检索限定在智能体绑定的知识库内，命中内容注入 system 提示词
        List<KnowledgeRetrievalResult> retrievalResults = logRagRetrieval(chatVO);
        String retrievalContext = buildRetrievalContext(retrievalResults);

        long startTime = System.currentTimeMillis();
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(chatVO.getAgentId());
        usageLog.setUserId(chatVO.getUserId());
        usageLog.setConversationId(parseConversationId(chatVO.getChatId()));
        usageLog.setStatus(1);
        usageLog.setCreateTime(new Date());

        StringBuilder systemPrompt = new StringBuilder();
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            systemPrompt.append(agent.getSystemPrompt());
        }
        if (!retrievalContext.isEmpty()) {
            if (systemPrompt.length() > 0) {
                systemPrompt.append("\n\n");
            }
            systemPrompt.append(retrievalContext);
        }

        var promptSpec = chatClient.prompt()
                .user(chatVO.getMessage())
                .system(systemPrompt.toString())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId()));

        if (toolCallbacks.length > 0) {
            promptSpec.toolCallbacks(toolCallbacks);
        }

        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();

        return promptSpec.stream().chatResponse()
                .doOnNext(response -> extractUsage(response, inputTokens, outputTokens))
                .map(response -> response.getResult().getOutput().getText())
                .doOnTerminate(() -> {
                    usageLog.setInputTokens(inputTokens.get());
                    usageLog.setOutputTokens(outputTokens.get());
                    usageLog.setResponseTime((int) (System.currentTimeMillis() - startTime));
                    agentUsageLogService.logUsage(usageLog);
                })
                .doOnError(error -> {
                    usageLog.setStatus(0);
                    usageLog.setErrorMsg(error.getMessage());
                });
    }

    private Long parseConversationId(String chatId) {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 执行知识库检索并记录检索日志；无绑定知识库时返回空列表
     */
    private List<KnowledgeRetrievalResult> logRagRetrieval(ChatVO chatVO) {
        long startTime = System.currentTimeMillis();
        try {
            List<KnowledgeRetrievalResult> results =
                    knowledgeRetrievalService.retrieveForAgent(chatVO.getAgentId(), chatVO.getMessage());

            List<Map<String, Object>> chunks = results.stream()
                    .map(r -> Map.<String, Object>of(
                            "chunkId", r.getChunkId() == null ? "" : r.getChunkId(),
                            "content", r.getText() == null ? "" : r.getText(),
                            "score", r.getScore() == null ? 0.0 : r.getScore(),
                            "kbId", r.getKbId() == null ? 0L : r.getKbId(),
                            "fileName", r.getFileName() == null ? "" : r.getFileName()
                    ))
                    .toList();

            Long firstKbId = results.isEmpty() ? null : results.get(0).getKbId();

            RagRetrievalLog retrievalLog = new RagRetrievalLog();
            retrievalLog.setAgentId(chatVO.getAgentId());
            retrievalLog.setUserId(chatVO.getUserId());
            retrievalLog.setKbId(firstKbId != null ? firstKbId : 0L);
            retrievalLog.setQuery(chatVO.getMessage());
            retrievalLog.setRetrievedChunks(JacksonUtil.writeValueAsString(chunks));
            retrievalLog.setTopK(results.size());
            retrievalLog.setScoreThreshold(0.4f);
            retrievalLog.setResponseTime((int) (System.currentTimeMillis() - startTime));
            retrievalLog.setCreateTime(new Date());
            ragRetrievalLogService.logRetrieval(retrievalLog);
            return results;
        } catch (Exception e) {
            log.error("记录RAG检索日志失败", e);
            return List.of();
        }
    }

    /**
     * 把命中分块拼装成可注入 system 提示词的上下文段落
     */
    private String buildRetrievalContext(List<KnowledgeRetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
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
        return sb.toString();
    }

    private void extractUsage(ChatResponse response,
                              java.util.concurrent.atomic.AtomicInteger inputTokens,
                              java.util.concurrent.atomic.AtomicInteger outputTokens) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            inputTokens.set(usage.getPromptTokens().intValue());
        }
        if (usage.getCompletionTokens() != null) {
            outputTokens.set(usage.getCompletionTokens().intValue());
        }
    }
}
