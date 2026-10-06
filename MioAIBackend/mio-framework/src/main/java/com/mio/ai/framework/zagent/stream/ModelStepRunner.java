package com.mio.ai.framework.zagent.stream;

import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.history.ConversationEntry;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模型步执行器（zcode runModelTextRequest 的对位移植）：
 * 流式调用 + 瞬态失败重试（zcode 重试边界：本轮已发出任何内容后不重试）。
 */
public final class ModelStepRunner {

    private static final long RETRY_BASE_DELAY_MS = 2_000;
    private static final long RETRY_MAX_DELAY_MS = 60_000;

    private final ChatModel chatModel;
    private final long streamTimeoutSeconds;
    private final String reasoningEffort;
    private final int modelRetries;

    /** 最近一步是否已流出部分正文（兜底收束时判断是否需要先分段） */
    private volatile boolean lastStepTextEmitted;

    public ModelStepRunner(ChatModel chatModel, long streamTimeoutSeconds, String reasoningEffort,
                           int modelRetries) {
        this.chatModel = chatModel;
        this.streamTimeoutSeconds = streamTimeoutSeconds;
        this.reasoningEffort = reasoningEffort;
        this.modelRetries = modelRetries;
    }

    /** 一步模型请求的结果 */
    public record StepResult(String text, List<ConversationEntry.ToolCallInput> toolCalls, Usage usage) {
    }

    public StepResult run(List<Message> messages, List<ToolCallback> toolCallbacks, AgentEvents events) {
        for (int attempt = 1; ; attempt++) {
            StreamTurnCollector collector = new StreamTurnCollector(
                    events::thinkingDelta, events::answerDelta, events::toolUse, events::toolArgs);
            try {
                Prompt prompt = new Prompt(messages, buildOptions(toolCallbacks));
                chatModel.stream(prompt).doOnNext(collector::accept).blockLast();
                lastStepTextEmitted = collector.hasEmittedText();
                return new StepResult(collector.getText(), collector.getToolCallInputs(), collector.getUsage());
            } catch (Throwable error) {
                lastStepTextEmitted = collector.hasEmittedText();
                StreamFailureClassifier.Classification failure = StreamFailureClassifier.classify(error);
                boolean canRetry = failure.retryable()
                        && !collector.hasEmitted()
                        && attempt <= modelRetries;
                if (!canRetry) {
                    throw error;
                }
                events.retryScheduled(attempt, modelRetries + 1, failure.reason());
                try {
                    Thread.sleep(retryDelayMs(attempt));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
    }

    /** 最近一步失败前是否已流出部分正文（引擎兜底收束时用于决定是否插入分段） */
    public boolean lastStepTextEmitted() {
        return lastStepTextEmitted;
    }

    /** 指数退避 + 抖动：min(60s, 2s × 2^(attempt-1))，±20% 随机化 */
    private static long retryDelayMs(int attempt) {
        long capped = Math.min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS << Math.min(attempt - 1, 5));
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble(0.4);
        return Math.round(capped * jitter);
    }

    /**
     * 挂工具与流式超时（必须用 OpenAiChatOptions：模型内部强转 prompt.options，
     * 裸 builder 默认 gpt-5-mini 会覆盖 yml 模型名；timeout 不设会被 okhttp 60s 默认掐断长流）。
     * 空工具列表不设 toolCallbacks——部分网关对 "tools":[] 直接 400（换模型兼容）。
     */
    private OpenAiChatOptions buildOptions(List<ToolCallback> toolCallbacks) {
        OpenAiChatOptions.Builder builder;
        if (chatModel.getDefaultOptions() instanceof OpenAiChatOptions defaults) {
            builder = defaults.mutate();
        } else {
            builder = OpenAiChatOptions.builder();
        }
        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            builder.toolCallbacks(toolCallbacks);
        }
        builder.timeout(Duration.ofSeconds(streamTimeoutSeconds));
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            builder.reasoningEffort(reasoningEffort);
        }
        return builder.build();
    }
}
