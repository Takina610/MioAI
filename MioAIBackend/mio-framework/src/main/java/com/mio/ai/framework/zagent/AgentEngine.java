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
    private int todosVersionSeen = -1;

    public AgentEngine(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                       ContextBuilder contextBuilder, ConversationState state, String chatId,
                       AgentEvents events, com.mio.ai.framework.zagent.task.BackgroundTasks tasks) {
        this.chatModel = chatModel;
        this.config = config;
        this.reasoningEffort = reasoningEffort;
        this.contextBuilder = contextBuilder;
        this.state = state;
        this.chatId = chatId;
        this.events = events;
        this.tasks = tasks;
        this.modelRunner = new ModelStepRunner(chatModel, config.streamTimeoutSeconds(),
                reasoningEffort, config.modelRetries());
        this.compactor = new ConversationCompactor(chatModel, config.contextWindowTokens());
    }

    /** 工具注册表在引擎构造后绑定（Agent 工具需要以引擎作为子代理启动器） */
    public void bindTools(ToolRegistry toolRegistry) {
        this.registry = toolRegistry;
        this.scheduler = new ToolScheduler(toolRegistry);
    }

    public long inputTokens() {
        return inputTokens;
    }

    public long outputTokens() {
        return outputTokens;
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

            ModelStepRunner.StepResult result = runStepWithReactiveCompact(messages, callbacks);
            if (result.usage() != null) {
                inputTokens += result.usage().getPromptTokens() != null
                        ? result.usage().getPromptTokens() : 0;
                outputTokens += result.usage().getCompletionTokens() != null
                        ? result.usage().getCompletionTokens() : 0;
            }
            events.stepFinished(step, inputTokens, outputTokens, result.toolCalls());
            state.add(ConversationEntry.assistant(result.text(), result.toolCalls()));

            if (result.toolCalls().isEmpty()) {
                return;
            }
            state.noteToolTurn();
            executeToolBatch(result.toolCalls());
        }
    }

    /** 上下文超限错误的反应式压缩（zcode recoverModelStepAfterContextExceeded） */
    private ModelStepRunner.StepResult runStepWithReactiveCompact(List<Message> messages,
                                                                  List<ToolCallback> callbacks) {
        try {
            return modelRunner.run(messages, callbacks, events);
        } catch (Throwable error) {
            if (!isContextExceeded(error) || compactNow() == null) {
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
            events.toolResult(id, result.toolName(), result.content());
            state.add(ConversationEntry.toolResult(id, calls.get(i).name(),
                    result.content(), result.error()));
        }
        emitTodosIfChanged();
    }

    private void emitTodosIfChanged() {
        int version = System.identityHashCode(state.todos()) + state.todos().size();
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
                        || lower.contains("context_length_exceeded")) {
                    return true;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
