package com.mio.ai.superagent.app;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.customagent.model.entity.AgentUsageLog;
import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.superagent.model.dto.SseChunk;
import com.mio.ai.superagent.model.vo.ChatVO;
import com.mio.ai.superagent.repository.ChatHistoryRepository;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: Takina
 * @date: 2026/4/4 19:49
 * @description:
 */

@Component
public class DefaultApp {
    @Autowired
    ChatHistoryRepository chatHistoryRepository;

    @Resource(name = "defaultChatClient")
    private ChatClient chatClient;

    @Autowired
    MessageChatMemoryAdvisor memoryAdvisor;

    @Autowired
    private AgentUsageLogService agentUsageLogService;

    public Flux<SseChunk> doChat(ChatVO chatVO) {
        var prompt = chatClient.prompt()
                .user(chatVO.getMessage());

        // 只有 userId 存在时，才启用记忆
        if (chatVO.getUserId() != null) {
            chatHistoryRepository.save(chatVO);

            prompt.advisors(memoryAdvisor).
                    advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatVO.getChatId())
            );
        }

        long startTime = System.currentTimeMillis();
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(chatVO.getAgentId());
        // 默认智能体对游客开放，user_id 非空约束下游客记为 0
        usageLog.setUserId(chatVO.getUserId() != null ? chatVO.getUserId() : 0L);
        usageLog.setConversationId(parseConversationId(chatVO.getChatId()));
        usageLog.setStatus(1);
        usageLog.setCreateTime(new Date());

        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();

        return prompt.stream().chatResponse()
                .doOnNext(response -> extractUsage(response, inputTokens, outputTokens))
                // Spring AI 2.0 流式末尾会推一条仅含 usage 的响应（getResult() 为 null），必须判空
                .<SseChunk>handle((response, sink) -> {
                    SseChunk chunk = toChunk(response);
                    if (chunk != null) {
                        sink.next(chunk);
                    }
                })
                // 流尾追加用量事件，随管道一起推送
                .concatWith(Mono.defer(() -> Mono.just(SseChunk.usage(
                        inputTokens.get(), outputTokens.get(),
                        System.currentTimeMillis() - startTime))))
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

    /** 推理模型的思考增量归 thinking，其余归 answer 正文 */
    private SseChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        var output = response.getResult().getOutput();
        Object reasoning = output.getMetadata().get("reasoningContent");
        if (reasoning instanceof String reasoningText && !reasoningText.isEmpty()) {
            return SseChunk.delta("thinking", reasoningText);
        }
        String text = output.getText();
        if (StrUtil.isEmpty(text)) {
            return null;
        }
        return SseChunk.delta("answer", text);
    }

    private Long parseConversationId(String chatId) {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void extractUsage(ChatResponse response, AtomicInteger inputTokens, AtomicInteger outputTokens) {
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
