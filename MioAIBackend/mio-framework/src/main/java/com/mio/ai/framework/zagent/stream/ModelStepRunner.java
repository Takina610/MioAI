package com.mio.ai.framework.zagent.stream;

import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.TurnStoppedException;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ModelStepRunner.class);

    private static final long RETRY_BASE_DELAY_MS = 2_000;
    private static final long RETRY_MAX_DELAY_MS = 60_000;

    /** 流式分片间 idle 超时（zcode stream-idle-timeout 对位）：防半开连接把单步挂满整个 callTimeout */
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofSeconds(600);
    /** 每次重试放宽 idle 超时，避免被同一上游的静默窗口反复打断 */
    private static final Duration IDLE_TIMEOUT_RETRY_INCREMENT = Duration.ofSeconds(30);
    /** 空补全重试上限（独立于主重试预算）：上游可能以 HTTP 200 返回零输出流 */
    private static final int EMPTY_COMPLETION_MAX_RETRIES = 1;

    private final ChatModel chatModel;
    private final long streamTimeoutSeconds;
    private final String reasoningEffort;
    private final int modelRetries;

    /** 最近一步是否已流出部分正文（兜底收束时判断是否需要先分段） */
    private volatile boolean lastStepTextEmitted;

    /** 轮次取消信号（用户中断）：每次尝试前与重试等待前检查，命中即停，不再重发/退避 */
    private volatile java.util.function.BooleanSupplier cancelSignal = () -> false;

    public void setCancelSignal(java.util.function.BooleanSupplier signal) {
        this.cancelSignal = signal;
    }

    public ModelStepRunner(ChatModel chatModel, long streamTimeoutSeconds, String reasoningEffort,
                           int modelRetries) {
        this.chatModel = chatModel;
        this.streamTimeoutSeconds = streamTimeoutSeconds;
        this.reasoningEffort = reasoningEffort;
        this.modelRetries = modelRetries;
    }

    /** 一步模型请求的结果（truncated=true 表示输出被 token 上限截断，工具调用已作废） */
    public record StepResult(String text, List<ConversationEntry.ToolCallInput> toolCalls, Usage usage,
                             boolean truncated) {

        public StepResult(String text, List<ConversationEntry.ToolCallInput> toolCalls, Usage usage) {
            this(text, toolCalls, usage, false);
        }
    }

    public StepResult run(List<Message> messages, List<ToolCallback> toolCallbacks, AgentEvents events) {
        int emptyCompletionRetries = 0;
        for (int attempt = 1; ; attempt++) {
            if (cancelSignal.getAsBoolean()) {
                throw new TurnStoppedException();
            }
            StreamTurnCollector collector = new StreamTurnCollector(
                    events::thinkingDelta, events::answerDelta, events::toolUse, events::toolArgs);
            try {
                Prompt prompt = new Prompt(messages, buildOptions(toolCallbacks));
                Duration idleTimeout = STREAM_IDLE_TIMEOUT.plus(
                        IDLE_TIMEOUT_RETRY_INCREMENT.multipliedBy(attempt - 1L));
                chatModel.stream(prompt).timeout(idleTimeout).doOnNext(collector::accept).blockLast();
                lastStepTextEmitted = collector.hasEmittedText();
                StepResult result = finishStep(collector, events);
                // 空补全（零文本+零工具+零用量）：多半是上游静默故障，作废本轮再试一次；
                // 已有可见输出或重试额度用尽则原样返回，由引擎的兜底收束接力
                if (isEmptyCompletion(result) && !collector.hasEmitted()
                        && emptyCompletionRetries < EMPTY_COMPLETION_MAX_RETRIES) {
                    emptyCompletionRetries++;
                    log.warn("模型流零输出（无文本/工具/用量），作废重试, attempt={}", attempt);
                    attempt--;
                    continue;
                }
                return result;
            } catch (Throwable error) {
                lastStepTextEmitted = collector.hasEmittedText();
                StreamFailureClassifier.Classification failure = StreamFailureClassifier.classify(error);
                boolean canRetry = failure.retryable()
                        && !collector.hasEmitted()
                        && attempt <= modelRetries;
                if (!canRetry) {
                    throw error;
                }
                if (cancelSignal.getAsBoolean()) {
                    throw new TurnStoppedException();
                }
                events.retryScheduled(attempt, modelRetries + 1, failure.reason());
                try {
                    Thread.sleep(retryDelayMs(attempt, failure.retryAfterMs()));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
    }

    /**
     * 收尾一步结果：输出被 token 上限截断（finishReason=length）时，半截的工具调用
     * 参数是 best-effort 补全的 JSON，"看起来合法实则不完整"——整体作废（pi 语义），
     * truncated 标记交给引擎做无工具收束，而不是把残缺调用送进执行器。
     */
    private StepResult finishStep(StreamTurnCollector collector, AgentEvents events) {
        String text = collector.getText();
        List<ConversationEntry.ToolCallInput> toolCalls = collector.getToolCallInputs();
        if ("length".equalsIgnoreCase(collector.getFinishReason()) && !toolCalls.isEmpty()) {
            log.warn("输出在工具调用中途被 token 上限截断，作废 {} 个工具调用", toolCalls.size());
            events.answerDelta("\n\n[输出因长度限制中断，截断的工具调用已丢弃，正在收束作答]");
            return new StepResult(text, List.of(), collector.getUsage(), true);
        }
        return new StepResult(text, toolCalls, collector.getUsage());
    }

    private static boolean isEmptyCompletion(StepResult result) {
        boolean noOutput = (result.text() == null || result.text().isBlank())
                && result.toolCalls().isEmpty();
        Usage usage = result.usage();
        boolean noUsage = usage == null
                || (isZero(usage.getPromptTokens()) && isZero(usage.getCompletionTokens()));
        return noOutput && noUsage;
    }

    private static boolean isZero(Integer count) {
        return count == null || count == 0;
    }

    /** 最近一步失败前是否已流出部分正文（引擎兜底收束时用于决定是否插入分段） */
    public boolean lastStepTextEmitted() {
        return lastStepTextEmitted;
    }

    /**
     * 重试等待：服务端给了合理的 Retry-After 就服从（codex），否则指数退避 + 抖动
     * min(60s, 2s × 2^(attempt-1))，±20% 随机化
     */
    private static long retryDelayMs(int attempt, long retryAfterMs) {
        if (retryAfterMs > 0) {
            return Math.round(retryAfterMs * (0.9 + ThreadLocalRandom.current().nextDouble(0.2)));
        }
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
