package com.mio.ai.framework.zagent;

import com.mio.ai.framework.zagent.compact.ConversationCompactor;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationEntry;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.history.HistoryProjector;
import com.mio.ai.framework.zagent.reminder.SystemReminders;
import com.mio.ai.framework.zagent.stream.ModelStepRunner;
import com.mio.ai.framework.zagent.tools.ToolRegistry;
import com.mio.ai.framework.zagent.tools.ToolScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 引擎（zcode AgentRuntime / runRegularTurnLoop 的对位移植）：
 * 模型步 → 工具调度执行 → 结果回灌 → 循环，直到模型给出无工具调用的最终回答。
 * <p>每步前做微压缩与自动压缩；上下文超限错误触发反应式压缩后重试一步；
 * 步数上限只是安全网（zcode 主循环本身无上限），最后一轮禁用工具强制收束。
 */
@Slf4j
public final class AgentEngine {

    private static final String STEP_LIMIT_HINT =
            "[system] The step limit for this turn has been reached. Do not call any more tools. "
                    + "Summarize the results obtained so far and suggest how to continue.";

    /** 兜底收束指令（不落会话历史，仅随抢救请求发出） */
    private static final String RESCUE_INSTRUCTION =
            "[system] The previous model step was interrupted or ended without any final answer text "
                    + "(the model service request failed mid-turn). Based on the conversation above and the "
                    + "tool results already obtained, provide your complete final answer to the user's "
                    + "request now. Do not call any more tools.";

    /** 兜底回复（用户可见）：抢救也失败时保证本轮必有落库回复，避免会话出现空结果 */
    private static final String TURN_FAILED_FALLBACK =
            "抱歉，模型服务连续多次请求失败，本轮未能生成回复。请点击重新生成，或稍后重新发送。";

    private final ChatModel chatModel;
    private final AgentEngineConfig config;
    private final String reasoningEffort;
    private final ContextBuilder contextBuilder;
    private final ConversationState state;
    private final String chatId;
    private final AgentEvents events;
    private final ModelStepRunner modelRunner;
    private final ConversationCompactor compactor;
    private final com.mio.ai.framework.zagent.task.BackgroundTasks tasks;

    private ToolRegistry registry;
    private ToolScheduler scheduler;
    private long inputTokens;
    private long outputTokens;
    private long cachedInputTokens;
    /** 上游是否回传过缓存明细（不回传的模型命中率按未知处理，而非误报 0） */
    private boolean cachedInputKnown;
    private int todosVersionSeen = -1;
    private int consecutiveReactiveCompacts;

    /** 轮次取消信号（用户中断）：步间与重试等待处检查，命中即抛 TurnStoppedException */
    private volatile java.util.function.BooleanSupplier cancelSignal = () -> false;

    /** 智能体级采样温度（null=跟随模型默认），一步不落直接透传给模型执行器 */
    public AgentEngine(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                       Double temperature, ContextBuilder contextBuilder, ConversationState state,
                       String chatId, AgentEvents events,
                       com.mio.ai.framework.zagent.task.BackgroundTasks tasks) {
        this.chatModel = chatModel;
        this.config = config;
        this.reasoningEffort = reasoningEffort;
        this.contextBuilder = contextBuilder;
        this.state = state;
        this.chatId = chatId;
        this.events = events;
        this.tasks = tasks;
        this.modelRunner = new ModelStepRunner(chatModel, config.streamTimeoutSeconds(),
                reasoningEffort, temperature, config.modelRetries());
        this.compactor = new ConversationCompactor(chatModel, config.contextWindowTokens());
    }

    /** 工具注册表在引擎构造后绑定（Agent 工具需要以引擎作为子代理启动器） */
    public void bindTools(ToolRegistry toolRegistry) {
        this.registry = toolRegistry;
        this.scheduler = new ToolScheduler(toolRegistry);
    }

    /** 注入取消信号（用户中断）；同步传给模型步执行器，重试等待同样响应 */
    public void setCancelSignal(java.util.function.BooleanSupplier signal) {
        this.cancelSignal = signal;
        this.modelRunner.setCancelSignal(signal);
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
    }

    /** 上游缓存命中的累计输入 token（网关不回传缓存明细时为 0） */
    public long cachedInputTokens() {
        return cachedInputTokens;
    }

    /** 上游是否回传过缓存明细（累计口径：任一步有明细即已知） */
    public boolean cachedInputKnown() {
        return cachedInputKnown;
    }

    /**
     * 执行一轮对话：注入提醒与用户输入 → 模型步循环。
     *
     * @param appendUserEntry false 表示用户输入已在水合历史末尾（重新生成场景）
     */
    public void runTurn(String userPrompt, boolean appendUserEntry) {
        ContextBuilder.Built built = contextBuilder.build();
        List<Message> prefix = new ArrayList<>(built.systemMessages());
        prefix.addAll(ContextBuilder.attachmentMessages(built.attachmentBodies()));

        for (String reminder : SystemReminders.collectTurnReminders(chatId, state, tasks)) {
            state.add(ConversationEntry.reminder("turn", reminder));
        }
        if (appendUserEntry) {
            state.add(ConversationEntry.user(userPrompt));
        }

        int step = 0;
        while (true) {
            step++;
            if (cancelSignal.getAsBoolean()) {
                throw new TurnStoppedException();
            }
            boolean finalStep = step >= config.maxSteps();
            compactor.microcompactIfNeeded(state);
            if (compactor.shouldAutoCompact(state)) {
                compactNow();
            }
            for (String reminder : SystemReminders.collectStepReminders(state)) {
                state.add(ConversationEntry.reminder("todo_reminder", reminder));
            }
            if (finalStep && step == config.maxSteps()) {
                state.add(ConversationEntry.reminder("turn", STEP_LIMIT_HINT));
            }

            List<Message> messages = HistoryProjector.project(prefix, state.entries());
            List<ToolCallback> callbacks = finalStep ? List.of() : registry.asToolCallbacks();

            ModelStepRunner.StepResult result = runStepOrFallback(messages, callbacks);

            // 输出被 token 上限截断且原本带工具调用（调用已作废）：强制无工具收束一次
            if (result.truncated()) {
                log.warn("模型步输出截断，工具调用已作废，收束作答, chatId={}, step={}", chatId, step);
                result = rescueOrFallback(messages);
            }
            // 模型空收尾（流被截断/工具调用幻觉后停住，只有思考没有答复）：抢救一次
            if (result.toolCalls().isEmpty() && isBlank(result.text())) {
                log.warn("模型步无文本无工具调用，兜底收束, chatId={}, step={}", chatId, step);
                result = rescueOrFallback(messages);
            }
            // 步数上限后模型仍要调工具：不再执行（防无上限循环与孤儿 tool_call），强制收束
            if (!result.toolCalls().isEmpty() && finalStep) {
                log.warn("步数上限后仍请求工具，强制收束, chatId={}, step={}", chatId, step);
                result = rescueOrFallback(messages);
            }

            accumulateUsage(result.usage());
            events.stepFinished(step, inputTokens, outputTokens, cachedInputTokens,
                    cachedInputKnown, result.toolCalls());
            state.add(ConversationEntry.assistant(result.text() == null ? "" : result.text(),
                    result.toolCalls()));

            if (result.toolCalls().isEmpty()) {
                return;
            }
            state.noteToolTurn();
            executeToolBatch(result.toolCalls());
        }
    }

    /** 一步模型请求：失败时兜底收束（抢救 → 静态文案），循环永不因模型服务失败中断 */
    private ModelStepRunner.StepResult runStepOrFallback(List<Message> messages,
                                                         List<ToolCallback> callbacks) {
        try {
            return runStepWithReactiveCompact(messages, callbacks);
        } catch (Throwable error) {
            if (cancelSignal.getAsBoolean()) {
                throw new TurnStoppedException();
            }
            if (isInterruption(error)) {
                throw error;
            }
            log.warn("模型步失败，进入兜底收束, chatId={}", chatId, error);
            // 与已流出的部分正文分段，避免兜底内容拼接在残句后
            if (modelRunner.lastStepTextEmitted()) {
                events.answerDelta("\n\n");
            }
            return rescueOrFallback(messages);
        }
    }

    /** 兜底收束：禁工具再调一次模型生成最终答复；再失败则落静态兜底文案（保证本轮必有可见回复） */
    private ModelStepRunner.StepResult rescueOrFallback(List<Message> messages) {
        if (cancelSignal.getAsBoolean()) {
            throw new TurnStoppedException();
        }
        try {
            List<Message> rescueMessages = new ArrayList<>(messages);
            rescueMessages.add(new org.springframework.ai.chat.messages.UserMessage(
                    HistoryProjector.wrapReminder(RESCUE_INSTRUCTION)));
            ModelStepRunner.StepResult rescue = modelRunner.run(rescueMessages, List.of(), events);
            if (!isBlank(rescue.text())) {
                return new ModelStepRunner.StepResult(rescue.text(), List.of(), rescue.usage());
            }
            log.warn("兜底收束返回空文本, chatId={}", chatId);
        } catch (Throwable error) {
            log.warn("兜底收束调用失败, chatId={}: {}", chatId, error.toString());
        }
        // 静态兜底：流式外发（进展示块/落库）+ 作为本轮 assistant 文本
        events.answerDelta(TURN_FAILED_FALLBACK);
        return new ModelStepRunner.StepResult(TURN_FAILED_FALLBACK, List.of(), null);
    }

    private void accumulateUsage(org.springframework.ai.chat.metadata.Usage usage) {
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            inputTokens += usage.getPromptTokens();
        }
        if (usage.getCompletionTokens() != null) {
            outputTokens += usage.getCompletionTokens();
        }
        // 缓存命中明细（OpenAI 通道取 prompt_tokens_details.cached_tokens；无明细的渠道为 null）
        Long cached = usage.getCacheReadInputTokens();
        if (cached != null) {
            cachedInputTokens += cached;
            cachedInputKnown = true;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** 中断类失败（线程被打断）不做兜底，原样上抛 */
    private static boolean isInterruption(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException
                    || Thread.currentThread().isInterrupted()) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** 反应式压缩连续触发上限（zcode rapid-refill 熔断）：压缩后马上又满说明有巨型单条内容，再压无用 */
    private static final int MAX_CONSECUTIVE_REACTIVE_COMPACTS = 3;

    /** 上下文超限错误的反应式压缩（zcode recoverModelStepAfterContextExceeded） */
    private ModelStepRunner.StepResult runStepWithReactiveCompact(List<Message> messages,
                                                                  List<ToolCallback> callbacks) {
        try {
            ModelStepRunner.StepResult result = modelRunner.run(messages, callbacks, events);
            consecutiveReactiveCompacts = 0;
            return result;
        } catch (Throwable error) {
            if (!isContextExceeded(error)) {
                throw error;
            }
            consecutiveReactiveCompacts++;
            if (consecutiveReactiveCompacts > MAX_CONSECUTIVE_REACTIVE_COMPACTS || compactNow() == null) {
                throw error;
            }
            ContextBuilder.Built rebuilt = contextBuilder.build();
            List<Message> retryMessages = new ArrayList<>(rebuilt.systemMessages());
            retryMessages.addAll(ContextBuilder.attachmentMessages(rebuilt.attachmentBodies()));
            retryMessages.addAll(HistoryProjector.project(List.of(), state.entries()));
            return modelRunner.run(retryMessages, callbacks, events);
        }
    }

    private void executeToolBatch(List<ConversationEntry.ToolCallInput> calls) {
        List<ToolRegistry.PendingCall> pending = calls.stream()
                .map(call -> new ToolRegistry.PendingCall(call.id(), call.name(), call.arguments()))
                .toList();
        List<ToolRegistry.Executed> executed = scheduler.executeAll(pending);
        for (int i = 0; i < executed.size(); i++) {
            ToolRegistry.Executed result = executed.get(i);
            String id = calls.get(i).id();
            // content 对媒体结果已是 [Attached ...] 占位文本，展示/落库不含 base64；
            // error/duration 供上层记工具日志真实状态
            events.toolResult(id, result.toolName(), result.content(), result.error(), result.durationMs());
            state.add(ConversationEntry.toolResult(id, calls.get(i).name(),
                    result.content(), result.error(), result.media()));
        }
        emitTodosIfChanged();
    }

    private void emitTodosIfChanged() {
        // TodoItem 是 record，List.hashCode 按内容计算——identityHashCode 会让同尺寸更新不推送
        int version = state.todos().hashCode();
        if (version != todosVersionSeen) {
            todosVersionSeen = version;
            events.todosChanged();
        }
    }

    /** 执行压缩；成功返回摘要，跳过/失败返回 null */
    public String compactNow() {
        String summary = compactor.compact(state);
        if (summary != null) {
            events.compacted(summary);
        }
        return summary;
    }

    public ConversationState state() {
        return state;
    }

    public ChatModel chatModel() {
        return chatModel;
    }

    public AgentEngineConfig config() {
        return config;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public ContextBuilder contextBuilder() {
        return contextBuilder;
    }

    public String chatId() {
        return chatId;
    }

    public AgentEvents events() {
        return events;
    }

    public static boolean isContextExceeded(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("context length") || lower.contains("context window")
                        || lower.contains("maximum context") || lower.contains("prompt is too long")
                        || lower.contains("context_length_exceeded") || lower.contains("prompt too long")
                        || lower.contains("input length exceed") || lower.contains("length exceeds")
                        || lower.contains("input token") || lower.contains("token limit")
                        || lower.contains("超过上限") || lower.contains("超出上限")
                        || lower.contains("上下文长度") || lower.contains("上下文过长")
                        || lower.contains("内容过长") || lower.contains("输入过长")
                        || lower.contains("token超限") || lower.contains("token 超限")) {
                    return true;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
