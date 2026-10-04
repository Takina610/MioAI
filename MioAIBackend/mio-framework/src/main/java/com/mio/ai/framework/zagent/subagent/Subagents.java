package com.mio.ai.framework.zagent.subagent;

import com.mio.ai.framework.zagent.AgentEngine;
import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.tools.ToolRegistry;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 子代理运行器（zcode runtime/methods/subagent.ts 的对位移植）：
 * 子运行时独立历史与上下文（只拿 prompt 文本），工具面按档案裁剪；
 * 工具调用以带前缀 id 镜像到父级事件流；后台模式入注册表并通知父会话。
 */
@Slf4j
public final class Subagents implements SubagentLauncher {

    private static final ExecutorService POOL = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "zagent-subagent");
        thread.setDaemon(true);
        return thread;
    });

    private final ChatModel chatModel;
    private final AgentEngineConfig config;
    private final String reasoningEffort;
    private final ToolsetFactory toolsetFactory;
    private final ToolsetFactory.RunContext runContext;
    private final AgentEvents parentEvents;
    private final String modelName;

    public Subagents(ChatModel chatModel, AgentEngineConfig config, String reasoningEffort,
                     ToolsetFactory toolsetFactory, ToolsetFactory.RunContext runContext,
                     AgentEvents parentEvents, String modelName) {
        this.chatModel = chatModel;
        this.config = config;
        this.reasoningEffort = reasoningEffort;
        this.toolsetFactory = toolsetFactory;
        this.runContext = runContext;
        this.parentEvents = parentEvents;
        this.modelName = modelName;
    }

    @Override
    public Result launch(String subagentType, String description, String prompt, boolean background) {
        AgentProfiles.Profile profile = AgentProfiles.resolve(subagentType);
        String agentId = "agent_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long startedAt = System.currentTimeMillis();
        if (background) {
            var task = runContext.tasks().register(runContext.chatId(), "agent",
                    description == null ? profile.name() : description);
            POOL.execute(() -> {
                Outcome outcome = runChild(profile, agentId, prompt);
                runContext.tasks().complete(task, outcome.failed(), 0, outcome.content());
            });
            return new Result(agentId, profile.name(), prompt,
                    "Agent launched in background (" + profile.name() + "). You will be notified when it "
                            + "completes; use TaskOutput with task_id \"" + task.id + "\" to wait for it.",
                    0, 0, 0, task.id);
        }
        Outcome outcome = runChild(profile, agentId, prompt);
        return new Result(agentId, profile.name(), prompt, outcome.content(),
                outcome.toolUseCount(), outcome.tokens(), System.currentTimeMillis() - startedAt, null);
    }

    private record Outcome(String content, int toolUseCount, long tokens, boolean failed) {
    }

    private Outcome runChild(AgentProfiles.Profile profile, String agentId, String prompt) {
        MirroringEvents childEvents = new MirroringEvents(parentEvents, agentId + "-");
        ConversationState childState = new ConversationState();
        ContextBuilder childContext = new ContextBuilder(
                buildChildSystemPrompt(profile), null, runContext.fs(), modelName);
        AgentEngine child = new AgentEngine(chatModel, config, reasoningEffort, childContext,
                childState, runContext.chatId() + "/" + agentId, childEvents, runContext.tasks());
        ToolRegistry childRegistry = toolsetFactory.build(runContext.chatId(), childState,
                runContext.mcpTools(), profile.tools(), null);
        child.bindTools(childRegistry);
        try {
            child.runTurn(prompt, true);
            String content = finalText(childState);
            return new Outcome(content, childEvents.toolUseCount.get(),
                    child.inputTokens() + child.outputTokens(), false);
        } catch (Exception e) {
            log.warn("子代理执行失败 ({}): {}", profile.name(), e.getMessage());
            return new Outcome("(Subagent failed: " + e.getMessage() + ")", childEvents.toolUseCount.get(),
                    0, true);
        } finally {
            childRegistry.shutdown();
        }
    }

    private String buildChildSystemPrompt(AgentProfiles.Profile profile) {
        return profile.systemPrompt() + "\n\n" + profile.description();
    }

    /** 子代理最终文本（最后一条助手消息） */
    private String finalText(ConversationState state) {
        for (int i = state.entries().size() - 1; i >= 0; i--) {
            var entry = state.entries().get(i);
            if (entry.kind == com.mio.ai.framework.zagent.history.ConversationEntry.Kind.ASSISTANT
                    && entry.text != null && !entry.text.isBlank()) {
                return entry.text;
            }
        }
        return "(Subagent completed but returned no output.)";
    }

    /** 父级事件镜像：工具调用可见，文本/思考不外流（zcode emitParentEvent 语义裁剪） */
    private static final class MirroringEvents implements AgentEvents {
        private final AgentEvents parent;
        private final String idPrefix;
        private final AtomicInteger toolUseCount = new AtomicInteger();

        private MirroringEvents(AgentEvents parent, String idPrefix) {
            this.parent = parent;
            this.idPrefix = idPrefix;
        }

        @Override
        public void thinkingDelta(String delta) {
            // 子代理思考不外流
        }

        @Override
        public void answerDelta(String delta) {
            // 子代理正文作为工具结果返回，不直接外流
        }

        @Override
        public void toolUse(String id, String name) {
            parent.toolUse(idPrefix + id, name);
        }

        @Override
        public void toolArgs(String id, String delta) {
            parent.toolArgs(idPrefix + id, delta);
        }

        @Override
        public void toolResult(String id, String name, String content) {
            toolUseCount.incrementAndGet();
            parent.toolResult(idPrefix + id, name, content);
        }

        @Override
        public void todosChanged() {
            // 子代理清单不推送父级计划面板
        }

        @Override
        public void retryScheduled(int attempt, int maxAttempts, String reason) {
            parent.retryScheduled(attempt, maxAttempts, reason);
        }

        @Override
        public void stepFinished(int step, long inputTokens, long outputTokens,
                                 List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
            // 子代理用量并入 usage 块汇报，不逐步上抛
        }

        @Override
        public void compacted(String summary) {
            // 子代理压缩不打扰父级
        }
    }
}
