package com.mio.ai.framework.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/4/1 14:56
 * @description: 自定义的敏感词处理 Advisor，只检查最新的用户消息
 */

public class ChineseSafeGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String DEFAULT_FAILURE_RESPONSE = "抱歉，您的问题涉及敏感内容，我无法回答。请换一种方式提问或讨论其他话题。";
    private static final int DEFAULT_ORDER = 0;

    private final String failureResponse;

    private final List<String> sensitiveWords;

    private final int order;

    public ChineseSafeGuardAdvisor(List<String> sensitiveWords) {
        this(sensitiveWords, DEFAULT_FAILURE_RESPONSE, DEFAULT_ORDER);
    }

    public ChineseSafeGuardAdvisor(List<String> sensitiveWords, String failureResponse, int order) {
        Assert.notNull(sensitiveWords, "Sensitive words must not be null!");
        Assert.notNull(failureResponse, "Failure response must not be null!");
        this.sensitiveWords = sensitiveWords;
        this.failureResponse = failureResponse;
        this.order = order;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 获取最新的用户消息文本
     * 只检查最后一条 UserMessage，避免历史消息中的敏感词导致重复拦截
     */
    private String getLatestUserMessageText(ChatClientRequest chatClientRequest) {
        List<Message> messages = chatClientRequest.prompt().getInstructions();
        if (CollectionUtils.isEmpty(messages)) {
            return null;
        }
        // 从后往前找最后一条用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.getMessageType() == MessageType.USER) {
                return message.getText();
            }
        }
        return null;
    }

    /**
     * 检查文本是否包含敏感词
     */
    private boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return !CollectionUtils.isEmpty(this.sensitiveWords) 
                && this.sensitiveWords.stream().anyMatch(text::contains);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String latestUserText = getLatestUserMessageText(chatClientRequest);
        if (containsSensitiveWord(latestUserText)) {
            return createFailureResponse(chatClientRequest);
        }
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {
        String latestUserText = getLatestUserMessageText(chatClientRequest);
        if (containsSensitiveWord(latestUserText)) {
            return Flux.just(createFailureResponse(chatClientRequest));
        }
        return streamAdvisorChain.nextStream(chatClientRequest);
    }

    private ChatClientResponse createFailureResponse(ChatClientRequest chatClientRequest) {
        return ChatClientResponse.builder()
                .chatResponse(ChatResponse.builder()
                        .generations(List.of(new Generation(new AssistantMessage(this.failureResponse))))
                        .build())
                .context(Map.copyOf(chatClientRequest.context()))
                .build();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public static final class Builder {

        private List<String> sensitiveWords;

        private String failureResponse = DEFAULT_FAILURE_RESPONSE;

        private int order = DEFAULT_ORDER;

        private Builder() {
        }

        public Builder sensitiveWords(List<String> sensitiveWords) {
            this.sensitiveWords = sensitiveWords;
            return this;
        }

        public Builder failureResponse(String failureResponse) {
            this.failureResponse = failureResponse;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public ChineseSafeGuardAdvisor build() {
            return new ChineseSafeGuardAdvisor(this.sensitiveWords, this.failureResponse, this.order);
        }

    }

}
