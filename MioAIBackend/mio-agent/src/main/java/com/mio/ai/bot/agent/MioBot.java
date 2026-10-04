package com.mio.ai.bot.agent;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.framework.agent.AgentLoop;
import com.mio.ai.framework.agent.AgentPrompts;
import com.mio.ai.framework.plan.AgentPlan;
import com.mio.ai.framework.plan.PlanningTool;
import com.mio.ai.framework.sandbox.SandboxSession;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MioBot：AgentLoop（framework 通用循环）之上的产品化封装。
 * <p>职责仅剩三件：①把循环事件翻译成 SSE 信封流（前端协议不变）；
 * ②会话记忆与展示消息持久化；③任务清单生命周期与心跳保活。
 * Agent 行为本身由 AgentPrompts + 通用原语工具池决定。
 * <p>由调用方按请求创建实例，不作为 Spring Bean 管理。
 */
@Slf4j
public class MioBot {

    /** agent 表中 MioBot 的固定 ID（日志与会话记录沿用该关联） */
    public static final long AGENT_ID = 1L;

    /** 单轮任务的步数上限兜底值；实际取配置 mio.ai.agent.max-steps（zcode 风格宽松上限） */
    private static final int DEFAULT_MAX_STEPS = 100;

    /** 心跳间隔：小于常见代理/网关的空闲超时，保证长工具执行期间连接存活 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    /** SSE 工具结果事件的预览长度：完整结果已进入模型上下文，前端只需可读摘要 */
    private static final int TOOL_RESULT_PREVIEW_LENGTH = 400;

    /** 心跳调度器（daemon 单线程，所有 run 共享） */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mio-bot-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final AgentUsageLogService agentUsageLogService;
    private final ToolCallLogService toolCallLogService;

    private final String chatId;
    private final Long userId;
    private final Long agentId;
    private final String baseSystemPrompt;
    /** 沙箱工作目录展示名；null = 沙箱未启用 */
    private final String sandboxWorkdir;
    private final int maxSteps;
    private final ToolCallback[] tools;

    private final AgentPlan plan = new AgentPlan();
    private final BotEventChannel channel;

    /** 任务结束（含异常）回调：控制器借此解除会话占用（防重复执行闸门） */
    private volatile Runnable onFinish;

    public MioBot(ChatModel chatModel,
                  ChatMemory chatMemory,
                  ToolCallback[] builtInTools,
                  List<ToolCallback> mcpTools,
                  AgentUsageLogService agentUsageLogService,
                  ToolCallLogService toolCallLogService,
                  AgentMessageService agentMessageService,
                  String chatId,
                  Long userId,
                  Long agentId,
                  String baseSystemPrompt,
                  SandboxSession sandboxSession,
                  Integer maxSteps) {
        this.chatModel = chatModel;
        this.chatMemory = chatMemory;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.chatId = chatId;
        this.userId = userId;
        this.agentId = agentId != null ? agentId : AGENT_ID;
        this.baseSystemPrompt = baseSystemPrompt;
        this.sandboxWorkdir = sandboxSession != null ? sandboxSession.workdirDisplay() : null;
        this.maxSteps = maxSteps != null && maxSteps > 0 ? maxSteps : DEFAULT_MAX_STEPS;
        this.tools = concatTools(builtInTools, mcpTools,
                ToolCallbacks.from(new PlanningTool(plan))[0]);
        this.channel = new BotEventChannel(agentMessageService, chatId, userId, agentId);
    }

    /**
     * 执行一轮对话任务，流式返回全过程事件。
     *
     * @param userPrompt        用户输入
     * @param knowledgeContext  知识库检索命中的上下文（可为空），注入系统提示词
     * @param persistUserMessage 为 false 时不重复落库用户消息（重新生成场景：截断后重发）
     */
    public SseEmitter run(String userPrompt, String knowledgeContext, boolean persistUserMessage) {
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        channel.bind(sseEmitter);
        plan.setChangeListener(this::emitPlan);

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                    channel::heartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
            try {
                List<Message> messages = new ArrayList<>(loadHistory());
                UserMessage userMessage = new UserMessage(userPrompt);
                messages.add(userMessage);
                if (persistUserMessage) {
                    persist(userMessage);
                    channel.persistDisplay("user", userPrompt, null);
                }

                String systemPrompt = AgentPrompts.build(
                        baseSystemPrompt, knowledgeContext, sandboxWorkdir != null, sandboxWorkdir);
                AgentLoop.Result result = new AgentLoop().run(
                        chatModel, messages, systemPrompt, tools, planSection(), maxSteps, loopListener());

                persistNarratives(messages);
                if (!result.finished()) {
                    channel.answerDelta("\n\n（已达到单轮任务的最大执行步数，以上是目前的执行结果，可以继续提问让我接着完成。）");
                }
                finalizePlan();
                long durationMs = System.currentTimeMillis() - startTime;
                channel.persistDisplay("assistant", null, durationMs);
                channel.usage((int) result.inputTokens(), (int) result.outputTokens(), durationMs);
                channel.done();
                complete(sseEmitter);
            } catch (Exception e) {
                log.error("MioBot 执行异常", e);
                channel.persistDisplay("assistant", null, System.currentTimeMillis() - startTime);
                channel.error("执行出错：" + e.getMessage());
                complete(sseEmitter);
            } finally {
                heartbeat.cancel(false);
                if (onFinish != null) {
                    try {
                        onFinish.run();
                    } catch (Exception ignored) {
                        // 清理回调失败不影响主流程
                    }
                }
            }
        });

        sseEmitter.onTimeout(() -> log.warn("MioBot SSE connection timeout, chatId={}", chatId));
        return sseEmitter;
    }

    /** 循环事件 → SSE 信封（工具结果按预览长度截断后展示） */
    private AgentLoop.Listener loopListener() {
        return new AgentLoop.Listener() {
            @Override
            public void thinkingDelta(String delta) {
                channel.thinkingDelta(delta);
            }

            @Override
            public void answerDelta(String delta) {
                channel.answerDelta(delta);
            }

            @Override
            public void toolUse(String id, String tool) {
                channel.toolUse(id, tool);
            }

            @Override
            public void toolArgs(String id, String delta) {
                channel.toolArgs(id, delta);
            }

            @Override
            public void toolResult(String id, String tool, String result) {
                String preview = result != null && result.length() > TOOL_RESULT_PREVIEW_LENGTH
                        ? result.substring(0, TOOL_RESULT_PREVIEW_LENGTH) + "…" : result;
                channel.toolResultPreview(id, tool, preview);
            }

            @Override
            public void stepFinished(ChatResponse response, int step, List<AssistantMessage.ToolCall> toolCalls) {
                logUsage(response, step);
                logToolCalls(toolCalls);
            }
        };
    }

    /**
     * 会话记忆回放：循环结束后把本轮叙述性内容落库——
     * 带工具调用的助手轮落"纯文本副本"（JdbcChatMemoryRepository 不支持工具消息），最终回答原样落库
     */
    private void persistNarratives(List<Message> messages) {
        for (Message message : messages) {
            if (!(message instanceof AssistantMessage assistant) || StrUtil.isBlank(assistant.getText())) {
                continue;
            }
            persist(assistant.hasToolCalls() ? new AssistantMessage(assistant.getText()) : assistant);
        }
    }

    /** 回读会话历史作为本轮上下文（跨请求不失忆）；失败时降级为空历史 */
    private List<Message> loadHistory() {
        try {
            List<Message> history = chatMemory.get(chatId);
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            log.warn("回读会话记忆失败，从空上下文开始: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 把当前任务清单注入系统提示词，让模型始终"看得见"计划进度 */
    private String planSection() {
        String planText = plan.render();
        if (planText.isEmpty()) {
            return "";
        }
        return "\n\n【当前任务清单】\n" + planText
                + "\n请按清单推进：完成的步骤及时标记 done，正在做的标记 in_progress。";
    }

    private ToolCallback[] concatTools(ToolCallback[] builtIn, List<ToolCallback> mcp, ToolCallback planning) {
        List<ToolCallback> all = new ArrayList<>();
        if (builtIn != null) {
            all.addAll(List.of(builtIn));
        }
        if (mcp != null) {
            all.addAll(mcp);
        }
        all.add(planning);
        return all.toArray(ToolCallback[]::new);
    }

    private void persist(Message message) {
        if (chatMemory == null || chatId == null || message == null) {
            return;
        }
        try {
            chatMemory.add(chatId, message);
        } catch (Exception e) {
            log.warn("会话记忆写入失败: {}", e.getMessage());
        }
    }

    private void logUsage(ChatResponse response, int step) {
        if (agentUsageLogService == null) {
            return;
        }
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(agentId);
        usageLog.setUserId(userId);
        usageLog.setConversationId(parseConversationId());
        usageLog.setStatus(1);
        usageLog.setCreateTime(new Date());
        var usage = response.getMetadata().getUsage();
        if (usage != null) {
            if (usage.getPromptTokens() != null) {
                usageLog.setInputTokens(usage.getPromptTokens().intValue());
            }
            if (usage.getCompletionTokens() != null) {
                usageLog.setOutputTokens(usage.getCompletionTokens().intValue());
            }
        }
        agentUsageLogService.logUsage(usageLog);
    }

    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCallLogService == null || toolCalls == null) {
            return;
        }
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            ToolCallLog toolCallLog = new ToolCallLog();
            toolCallLog.setAgentId(agentId);
            toolCallLog.setUserId(userId);
            toolCallLog.setConversationId(parseConversationId());
            toolCallLog.setToolId(0L);
            toolCallLog.setName(toolCall.name());
            toolCallLog.setInputParams(toolCall.arguments());
            toolCallLog.setStatus(1);
            toolCallLog.setCreateTime(new Date());
            toolCallLogService.logToolCall(toolCallLog);
        }
    }

    /** 任务清单变化：把结构化步骤推给前端渲染为计划面板 */
    private void emitPlan() {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (AgentPlan.Step step : plan.snapshot()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", step.index());
            item.put("description", step.description());
            item.put("status", step.status().name().toLowerCase());
            steps.add(item);
        }
        channel.plan(steps);
    }

    /**
     * 收尾同步：模型给出最终回答却没把清单余下步骤标记完成时统一补成已完成，
     * 保证前端清单与结论一致。
     */
    private void finalizePlan() {
        for (AgentPlan.Step step : plan.snapshot()) {
            if (step.status() != AgentPlan.StepStatus.DONE && step.status() != AgentPlan.StepStatus.FAILED) {
                plan.updateStatus(step.index(), AgentPlan.StepStatus.DONE);
            }
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 连接已被容器关闭
        }
    }

    public void setOnFinish(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    private Long parseConversationId() {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
