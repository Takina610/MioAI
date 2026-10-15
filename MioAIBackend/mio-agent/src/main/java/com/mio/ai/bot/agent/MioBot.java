package com.mio.ai.bot.agent;

import com.mio.ai.bot.model.dto.AttachmentItem;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.service.SkillRuntime;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import com.mio.ai.framework.zagent.AgentEngine;
import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationHydrator;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.history.TodoItem;
import com.mio.ai.framework.zagent.hooks.AgentHooks;
import com.mio.ai.framework.zagent.subagent.Subagents;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import com.mio.ai.framework.zagent.tools.ToolRegistry;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MioBot：zagent 引擎（zcode AgentRuntime 对位移植）之上的产品化封装。
 * <p>职责：①把引擎事件翻译成 SSE 信封流（前端协议不变）；②agent_message 展示持久化
 * 与请求历史水合；③心跳保活与用量/工具日志。Agent 行为完全由 zagent 决定。
 */
@Slf4j
public class MioBot {

    /** agent 表中 MioBot 的固定 ID（日志与会话记录沿用该关联） */
    public static final long AGENT_ID = 1L;

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mio-bot-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final ChatModel chatModel;
    private final ToolsetFactory toolsetFactory;
    private final List<ToolCallback> mcpTools;
    private final List<Skill> skills;
    private final AgentUsageLogService agentUsageLogService;
    private final ToolCallLogService toolCallLogService;
    private final com.mio.ai.resource.service.log.UserStatsDailyService userStatsDailyService;
    private final com.mio.ai.resource.service.log.SkillUsageLogService skillUsageLogService;
    private final AgentMessageService agentMessageService;
    private final com.mio.ai.bot.service.ChatFileService chatFileService;
    private final com.mio.ai.bot.service.image.ImageSearchService imageSearchService;
    private final SandboxFileTransfer fileTransfer;

    private final String chatId;
    private final Long userId;
    private final long agentId;
    private final String customSystemPrompt;
    private final String modelName;
    private final String reasoningEffort;
    private final AgentEngineConfig config;

    /** 本轮用户附件（可空）：落库展示 + 注入模型上下文 */
    private final List<AttachmentItem> attachments;

    private final BotEventChannel channel;

    /** 任务结束（含异常）回调：控制器借此解除会话占用 */
    private volatile Runnable onFinish;

    /** 用户主动中断标记：stop() 置位，引擎取消信号据此熔断步循环 */
    private volatile boolean stopped;

    /**
     * 本轮是否使用免费档模型（控制器注入）：免费档出现模型服务侧故障时，
     * 说明 <b>免费模型目录</b>可能已陈旧（模型被上游下线/改名），需要失效重建。
     * 自定义供应商模型与目录无关，不触发。
     */
    private volatile boolean freeCatalogModel;

    /** 免费模型目录失效动作（控制器注入，缺省空操作）：保持本类与目录实现解耦 */
    private volatile Runnable catalogInvalidator = () -> {
    };

    public void setFreeCatalogModel(boolean freeCatalogModel) {
        this.freeCatalogModel = freeCatalogModel;
    }

    public void setCatalogInvalidator(Runnable catalogInvalidator) {
        this.catalogInvalidator = catalogInvalidator == null ? () -> {
        } : catalogInvalidator;
    }

    /**
     * 模型服务侧故障处理（非用户取消、非上下文超限）：
     * 免费档模型失效缓存目录并异步重取最新列表覆盖。
     * 多轮并发出错时目录层自带冷却，重复调用无副作用。
     */
    private void onModelFailure(String reason) {
        if (!freeCatalogModel) {
            return;
        }
        log.warn("免费档模型服务侧故障（{}），失效免费模型目录并重取, chatId={}, model={}",
                reason, chatId, modelName);
        try {
            catalogInvalidator.run();
        } catch (Throwable ignored) {
            // 目录自愈失败不影响对话轮次收尾
        }
    }

    /** 轮次执行线程：stop() 据此打断模型流/工具等待 */
    private volatile Thread turnThread;

    /** 编辑重发场景：本轮 user 行的版本组锚（沿用被编辑轮次的组） */
    private volatile Long userGroupSeq;

    /** 本轮 MCP 装配失败摘要（控制器注入）：非空时会话开头发瞬态系统提示 */
    private volatile List<String> mcpFailures;

    /** 本轮知识库检索命中摘要（控制器注入）：回答实际引用时在收尾落引用行 */
    private volatile List<Map<String, Object>> kbGroups;

    /** 输入框引用上下文（控制器注入）：注入本轮模型提示词的引用材料正文 */
    private volatile String refsContext;

    /** 输入框引用清单（控制器注入）：随 user 行持久化为展示块（前端 chips 还原） */
    private volatile List<Map<String, Object>> userRefs;

    /** 群聊协调器（控制器注入，普通会话为 null）：Agent 工具的 subagent_type 命中成员名时以成员自身配置运行 */
    private volatile GroupCoordinator groupCoordinator;

    public void setGroupCoordinator(GroupCoordinator groupCoordinator) {
        this.groupCoordinator = groupCoordinator;
    }

    public void setUserGroupSeq(Long userGroupSeq) {
        this.userGroupSeq = userGroupSeq;
    }

    public void setRefsContext(String refsContext) {
        this.refsContext = refsContext;
    }

    public void setUserRefs(List<Map<String, Object>> userRefs) {
        this.userRefs = userRefs;
    }

    public void setMcpFailures(List<String> mcpFailures) {
        this.mcpFailures = mcpFailures;
    }

    public void setKbGroups(List<Map<String, Object>> kbGroups) {
        this.kbGroups = kbGroups;
    }

    public MioBot(ChatModel chatModel,
                  ToolsetFactory toolsetFactory,
                  List<ToolCallback> mcpTools,
                  List<Skill> skills,
                  AgentUsageLogService agentUsageLogService,
                  ToolCallLogService toolCallLogService,
                  com.mio.ai.resource.service.log.UserStatsDailyService userStatsDailyService,
                  com.mio.ai.resource.service.log.SkillUsageLogService skillUsageLogService,
                  AgentMessageService agentMessageService,
                  com.mio.ai.bot.service.ChatFileService chatFileService,
                  com.mio.ai.bot.service.image.ImageSearchService imageSearchService,
                  String chatId,
                  Long userId,
                  Long agentId,
                  String customSystemPrompt,
                  String modelName,
                  String reasoningEffort,
                  AgentEngineConfig config,
                  SandboxFileTransfer fileTransfer,
                  List<AttachmentItem> attachments) {
        this.chatModel = chatModel;
        this.toolsetFactory = toolsetFactory;
        this.mcpTools = mcpTools;
        this.skills = skills == null ? List.of() : skills;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.userStatsDailyService = userStatsDailyService;
        this.skillUsageLogService = skillUsageLogService;
        this.agentMessageService = agentMessageService;
        this.chatFileService = chatFileService;
        this.imageSearchService = imageSearchService;
        this.chatId = chatId;
        this.userId = userId;
        this.agentId = agentId != null ? agentId : AGENT_ID;
        this.customSystemPrompt = customSystemPrompt;
        this.modelName = modelName;
        this.reasoningEffort = reasoningEffort;
        this.config = config;
        this.attachments = attachments == null ? List.of() : attachments;
        this.fileTransfer = fileTransfer;
        this.channel = new BotEventChannel(agentMessageService, chatId, userId, agentId);
        // 技能使用落库：模型读 skills/<id>/ 即视为使用（与前端技能行判定同源，每轮每技能一条）
        if (skillUsageLogService != null && userId != null) {
            this.channel.setSkillUseListener((skillId, skillName) ->
                    skillUsageLogService.record(userId, this.agentId, parseConversationId(), skillId, skillName));
        }
        // MCP 工具描述表：tool_use 行附带工具描述（前端展示名称与描述，对齐内置工具行）
        Map<String, String> mcpToolDesc = new LinkedHashMap<>();
        for (ToolCallback callback : this.mcpTools) {
            try {
                mcpToolDesc.put(callback.getToolDefinition().name(),
                        callback.getToolDefinition().description());
            } catch (Exception ignored) {
                // 单个回调定义异常不阻断装配
            }
        }
        this.channel.setMcpToolDesc(mcpToolDesc);
        // 技能名表：工具读取 skills/<id>/ 文件时在工具行标注来源技能（技能是否使用由模型按需决定）
        Map<String, String> skillNames = new LinkedHashMap<>();
        for (Skill skill : this.skills) {
            skillNames.put(String.valueOf(skill.getId()), skill.getName());
        }
        this.channel.setSkillNames(skillNames);
    }

    /**
     * 执行一轮对话任务，流式返回全过程事件。
     *
     * @param userPrompt        用户输入（"/compact" 触发手动压缩）
     * @param knowledgeContext  知识库检索命中的上下文（可为空），以附件注入
     * @param persistUserMessage 为 false 时不重复落库用户消息（重新生成场景：截断后重发）
     */
    /** 轮次开始时间（epoch ms）：/bot/active-chats 携带，前端多会话并发时"最新会话优先"排序 */
    private volatile long turnStartedAt;

    public long getAgentId() {
        return agentId;
    }

    public long getTurnStartedAt() {
        return turnStartedAt;
    }

    public SseEmitter run(String userPrompt, String knowledgeContext, boolean persistUserMessage) {
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        channel.bind(sseEmitter);
        turnStartedAt = System.currentTimeMillis();

        // 专用轮次池：不占 ForkJoinPool.commonPool（阻塞任务会饿死全 JVM 的 CompletableFuture）
        if (!TurnExecutors.execute(() -> runTurnTask(userPrompt, knowledgeContext,
                persistUserMessage, sseEmitter))) {
            completeWithBusy(sseEmitter);
        }

        sseEmitter.onTimeout(() -> log.warn("MioBot SSE connection timeout, chatId={}", chatId));
        return sseEmitter;
    }

    private void runTurnTask(String userPrompt, String knowledgeContext, boolean persistUserMessage,
                             SseEmitter sseEmitter) {
        turnThread = Thread.currentThread();
        long startTime = System.currentTimeMillis();
        ScheduledFuture<?> heartbeat = HEARTBEAT_SCHEDULER.scheduleAtFixedRate(
                channel::heartbeat, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        ToolRegistry registry = null;
        AgentEngine engine = null;
        try {
            // MCP 装配失败项在会话开头明说（瞬态提示不落库，重载后不保留）
            if (mcpFailures != null && !mcpFailures.isEmpty()) {
                String joined = String.join("；", mcpFailures);
                channel.notice("以下 MCP 工具本轮不可用：" + (joined.length() > 300
                        ? joined.substring(0, 300) + "..." : joined));
            }
            ConversationHydrator.Hydrated hydrated = hydrate();
            ConversationState state = new ConversationState();
            state.entries().addAll(hydrated.entries());
            state.setTodos(hydrated.todos());

            BotAgentEvents events = new BotAgentEvents(state);
            // 技能物化到沙箱 skills/ 目录，并生成注入上下文的技能清单段（无技能/沙箱不可用时为 null）
            SandboxFs fs = toolsetFactory.sandboxFs();
            String skillsContext = SkillRuntime.materialize(skills, fs);
            // 工具调用钩子：审计落库从事件通道解耦为可插拔钩子（引擎核心不再硬编码横切关注点）
            AgentHooks toolHooks = AgentHooks.chain(List.of(new ToolAuditHook()));
            ToolsetFactory.RunContext runContext = new ToolsetFactory.RunContext(
                    chatId, fs, mcpTools,
                    com.mio.ai.framework.zagent.task.BackgroundTasks.instance(), toolHooks);
            ContextBuilder contextBuilder = new ContextBuilder(
                    customSystemPrompt, knowledgeContext, skillsContext, runContext.fs(), modelName,
                    userId != null ? agentId : null);
            engine = new AgentEngine(chatModel, config, reasoningEffort, contextBuilder,
                    state, chatId, events, runContext.tasks());
            // 用户中断：引擎步间/重试等待处检查取消信号，命中即停（不走兜底抢救）
            engine.setCancelSignal(() -> stopped);
            // 模型服务侧故障自愈：免费档模型出错说明目录可能已陈旧（模型被上游下线/改名），
            // 失效 Redis 目录并异步重取；自定义供应商模型、用户取消、上下文超限都不触发
            engine.setModelFailureListener(this::onModelFailure);

            if (userPrompt.strip().startsWith("/compact")) {
                // 压缩指令也是一条消息：user 行落库（带 compact 引用块，前端渲染为 Compact token）
                channel.setUserRefs(List.of(java.util.Map.of("type", "compact")));
                channel.persistDisplay("user", userPrompt, null, userGroupSeq);
                runManualCompact(engine, hydrated);
            } else {
                // 群聊成员用量按成员自身身份落库（agent_usage_log 记成员 agentId + 每日累计），
                // 群聊统计里"实际调用的智能体"由此可分；回调失败已被框架侧吞掉
                com.mio.ai.framework.zagent.subagent.MemberUsageListener memberUsageListener =
                        userId == null ? null
                                : (memberAgentId, inTok, outTok, durationMs, failed) -> {
                                    logMemberUsage(memberAgentId, inTok, outTok, failed);
                                    userStatsDailyService.accumulate(userId, inTok, outTok, 0, 0);
                                };
                Subagents launcher = new Subagents(chatModel, config, reasoningEffort,
                        toolsetFactory, runContext, events, modelName, groupCoordinator,
                        groupCoordinator != null ? new GroupMemberPresenter() : null, memberUsageListener);
                // 群聊：成员委派以平等消息块呈现，Agent 工具行不进主消息流
                channel.setSuppressAgentToolRows(groupCoordinator != null);
                registry = toolsetFactory.build(chatId, state, mcpTools, null, launcher, events, toolHooks);
                // 动态 UI 块工具：仅主轮次注册（子代理注册表不含），校验通过的块经通道下发/落库；
                // 图片块按本会话 image_search 登记簿核对 url（防伪造）并回填来源元数据
                registry.register(UiRenderTool.entry(channel,
                        url -> imageSearchService.resolveImage(chatId, url)));
                // 图片检索工具（图文混排）：模型在实体介绍等场景自主决定是否调用
                registry.register(ImageSearchTool.entry(imageSearchService));
                engine.bindTools(registry);

                boolean appendUserEntry = persistUserMessage || state.entries().isEmpty();
                if (persistUserMessage) {
                    channel.setUserAttachments(attachments);
                    channel.setUserRefs(userRefs);
                    channel.persistDisplay("user", userPrompt, null, userGroupSeq);
                }
                // WIP 行：轮次开始即落库，此后每个模型步增量刷新（进程崩溃后过程可恢复）
                channel.beginAssistantWip();
                // 轮次开始前刷新脱离式后台任务：沙箱侧已完结的补状态并入队 task-notification
                runContext.tasks().refreshDetached(chatId, runContext.fs());
                java.util.Set<String> outputsBefore = snapshotOutputs(runContext.fs());
                engine.runTurn(buildModelPrompt(userPrompt, runContext.fs()), appendUserEntry);
                collectOutputs(runContext.fs(), outputsBefore);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            channel.abortRunningTools();
            // 知识库引用行：回答实际引用了检索片段才出现（引用驱动的"使用"信号，弱相似噪声不显示）
            channel.emitKbCitations(kbGroups);
            // 压缩轮次可能无任何展示输出（compact 事件行是独立落库的）：跳过空 assistant 行
            boolean persisted = true;
            if (channel.hasDisplayBlocks()) {
                persisted = channel.persistDisplay("assistant", null, durationMs);
            }
            channel.finishAssistantWip(persisted);
            channel.usage((int) engine.inputTokens(), (int) engine.outputTokens(), durationMs);
            // 每日用量累计（贡献图/连续天数/峰值/聊天时长）：正常收尾整轮记一次
            accumulateDailyStats(engine, durationMs, 1);
            channel.done();
            complete(sseEmitter);
        } catch (Throwable e) {
            if (isStoppedTurn(e)) {
                // 用户主动中断：悬挂工具块收尾 + 部分过程落库（带中断标记）+ stopped 事件。
                // 不落 durationMs——前端据 stopped 标记显示"已停止"，不渲染"已完成"折叠行
                log.info("MioBot 轮次被用户中断, chatId={}", chatId);
                channel.abortRunningTools();
                channel.stopped();
                boolean persisted = channel.persistDisplay("assistant", null, null);
                channel.finishAssistantWip(persisted);
                complete(sseEmitter);
            } else {
                log.error("MioBot 执行异常, chatId={}", chatId, e);
                channel.abortRunningTools();
                boolean persisted = channel.persistDisplay("assistant", null, System.currentTimeMillis() - startTime);
                channel.finishAssistantWip(persisted);
                channel.error("执行出错：" + e.getMessage());
                // 异常轮次同样消耗了资源：token/耗时按实际值计入每日累计
                accumulateDailyStats(engine, System.currentTimeMillis() - startTime, 1);
                complete(sseEmitter);
            }
        } finally {
            heartbeat.cancel(false);
            if (registry != null) {
                registry.shutdown();
            }
            turnThread = null;
            // 清掉中断标记：轮次线程归还线程池，残留标记会让下一轮任务误判为中断
            Thread.interrupted();
            if (onFinish != null) {
                try {
                    onFinish.run();
                } catch (Exception ignored) {
                    // 清理回调失败不影响主流程
                }
            }
        }
    }

    /** 用户主动中断：置停止标记并打断轮次线程（模型流 blockLast/工具 future.get/重试 sleep 均响应中断） */
    public void stop() {
        this.stopped = true;
        Thread t = this.turnThread;
        if (t != null) {
            t.interrupt();
        }
    }

    /** 断线重连：把新连接挂到事件通道上，回放 lastSeq 之后的事件并续播直播 */
    public void attachReplay(SseEmitter emitter, long lastSeq) {
        channel.attachReplay(emitter, lastSeq);
    }

    /** 重连回放是否已对本轮停用（缓冲超上限） */
    public boolean isReplayDisabled() {
        return channel.isReplayDisabled();
    }

    /** 本轮是否为用户中断收尾：stop 标记/引擎停止异常/线程中断（沿 cause 链查） */
    private boolean isStoppedTurn(Throwable error) {
        if (stopped || error instanceof com.mio.ai.framework.zagent.TurnStoppedException) {
            return true;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** 轮次池打满：明确告知用户而非静默排队 */
    private void completeWithBusy(SseEmitter sseEmitter) {
        try {
            SseStreams.sendTyped(sseEmitter, SseChunk.content("error",
                    "系统繁忙：当前对话任务数已达上限，请稍后重试。").fields(), 1);
            SseStreams.sendTyped(sseEmitter, SseChunk.done().fields(), 2);
        } catch (Exception ignored) {
            // 连接已断开
        }
        complete(sseEmitter);
        log.warn("轮次线程池已满，拒绝任务, chatId={}", chatId);
        // 轮次任务未跑，onFinish 不会经 finally 触发：这里补上，否则 ACTIVE_CHATS 占用泄漏
        if (onFinish != null) {
            try {
                onFinish.run();
            } catch (Exception ignored) {
                // 清理回调失败不影响主流程
            }
        }
    }

    /** /compact：手动压缩（zcode executeManualCompact 对位）。压缩事件本身是独立的消息行（compact 块），无需附文 */
    private void runManualCompact(AgentEngine engine, ConversationHydrator.Hydrated hydrated) {
        if (hydrated.entries().size() < 4) {
            channel.answerDelta("会话内容还很少，暂时无需压缩。");
            return;
        }
        String summary = engine.compactNow();
        if (summary == null) {
            channel.answerDelta("上下文内容还不足以生成摘要，已跳过本次压缩。");
        }
    }

    /**
     * UI 意图助推模式：问题命中对比/解释/分析/规划类意图时，以轮次级 system-reminder
     * 提醒模型优先用 render_ui 块承载（弱模型对系统段的规则注意力不稳定，命中意图的
     * 就地提醒把决策拉回规则）。仅提醒不强制——显式要求纯文字/纯文本的问题不注入。
     */
    private static final java.util.regex.Pattern UI_INTENT_PATTERN = java.util.regex.Pattern.compile(
            "区别|差异|对比|比较|哪个更|哪个好|优缺点|difference|compare|vs\\.?|介绍|简介|是什么|什么是|"
                    + "讲讲|解释|概念|原理|分析|趋势|统计|计划|规划|安排|路线|清单|整理|步骤|流程|"
                    + "正常吗|好不好|健康|作息|睡眠|建议|推荐|方案|"
                    + "explain|introduce|what is|plan|roadmap|checklist|how to");
    private static final java.util.regex.Pattern PLAIN_TEXT_PATTERN = java.util.regex.Pattern.compile(
            "纯文字|纯文本|只用文字|不要.*(图|卡|表|组件|UI)|plain text|no (charts?|cards?|tables?)");

    /** 图片意图助推模式：问题涉及具象视觉实体（角色/人物/景点/产品等）时，提醒可走 image_search 图文链路 */
    private static final java.util.regex.Pattern IMAGE_INTENT_PATTERN = java.util.regex.Pattern.compile(
            "动漫|漫画|动画|角色|主角|人物|明星|景点|地标|旅游|旅行|风景|城市|外观|长什么样|照片|图集|手办|"
                    + "anime|manga|character|landmark|travel|scenic|destination|photos?");

    /** 模型可见的用户输入：附件/引用上下文以 system-reminder 注入（路径+材料正文），展示正文保持原样 */
    private String buildModelPrompt(String userPrompt, SandboxFs fs) {
        StringBuilder sb = new StringBuilder(userPrompt);
        String refs = refsContext;
        if (refs != null && !refs.isBlank()) {
            sb.append("\n\n<system-reminder>\n").append(refs).append("\n</system-reminder>");
        }
        if (!attachments.isEmpty() && fs != null && fileTransfer != null && fileTransfer.available()) {
            sb.append("\n\n<system-reminder>\n# 用户附件\n用户随本条消息上传了文件（上传已在对话外完成，"
                    + "文件就在沙箱工作区——不存在任何上传工具，不要尝试上传/下载，直接用文件工具读取）：\n");
            for (AttachmentItem item : attachments) {
                sb.append("- `").append(item.path()).append('`');
                String storedName = item.path().substring(item.path().lastIndexOf('/') + 1);
                if (item.name() != null && !item.name().isBlank() && !item.name().equals(storedName)) {
                    sb.append("（原始文件名: ").append(item.name()).append('）');
                }
                if (item.size() > 0) {
                    sb.append("，").append(humanSize(item.size()));
                }
                sb.append('\n');
            }
            sb.append("如需把处理后的文件交付给用户，写入 outputs/").append(SandboxFileTransfer.safeDir(chatId))
                    .append("/ 目录（已创建）：本轮结束后其中的新文件会自动作为可下载附件展示给用户。\n</system-reminder>");
        }
        String prompt = sb.toString();
        if (UI_INTENT_PATTERN.matcher(prompt).find() && !PLAIN_TEXT_PATTERN.matcher(prompt).find()) {
            prompt += "\n\n<system-reminder>\n"
                    + "This question looks like a comparison / explanation / analysis / planning request. "
                    + "Per the Structured UI blocks section, carry the main content in render_ui blocks "
                    + "(e.g. a comparison table + key-takeaway cards for a comparison; fact cards for an "
                    + "introduction; a chart + stat cards for data; phase cards + tasks for a plan; advice "
                    + "cards for recommendations), and keep Markdown prose only for the lead-in and "
                    + "interpretation. One call carrying 2-4 blocks is usually enough — render each piece of "
                    + "content once and do not repeat it in later calls.\n</system-reminder>";
        }
        if (IMAGE_INTENT_PATTERN.matcher(prompt).find() && !PLAIN_TEXT_PATTERN.matcher(prompt).find()) {
            prompt += "\n\n<system-reminder>\n"
                    + "This question is about a concrete visual entity (a character, person, landmark, "
                    + "destination or product). Call image_search first (query in English or romaji; "
                    + "category=\"anime\" for anime/manga characters or works; one call is usually enough), "
                    + "then render the returned candidates in the SAME turn with image blocks "
                    + "(image_list for an entity line-up, image_gallery for scenery, image/figure for a "
                    + "single illustration) using the returned URLs verbatim — a search followed by a "
                    + "text-only answer is a failure. Skip image_search only when images clearly add "
                    + "nothing or the user asked for plain text.\n</system-reminder>";
        }
        return prompt;
    }

    /** 本轮产出目录（工作区内相对路径，chatId 已净化） */
    private String outputsDir() {
        return SandboxFileTransfer.OUTPUTS_ROOT + "/" + SandboxFileTransfer.safeDir(chatId);
    }

    /** 轮次开始：创建产出目录并快照既有文件（结束时的差集即本轮新产出） */
    private java.util.Set<String> snapshotOutputs(SandboxFs fs) {
        if (fileTransfer == null || !fileTransfer.available() || fs == null) {
            return null;
        }
        try {
            fileTransfer.ensureOutputDir(chatId);
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Map<String, Object> item : fileTransfer.listDir(outputsDir())) {
                names.add(String.valueOf(item.get("name")));
            }
            return names;
        } catch (Exception e) {
            log.warn("产出目录快照失败, chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /** 轮次结束：本轮新写入 outputs/&lt;chatId&gt;/ 的文件作为附件块推给前端（含落库） */
    private void collectOutputs(SandboxFs fs, java.util.Set<String> before) {
        if (fileTransfer == null || !fileTransfer.available() || fs == null || before == null) {
            return;
        }
        try {
            List<Map<String, Object>> produced = fileTransfer.listNew(outputsDir(), before);
            if (!produced.isEmpty()) {
                channel.attachments(produced);
                if (chatFileService != null) {
                    // 台账登记产出文件：会话删除时按台账清目录（登记失败不影响交付）
                    chatFileService.registerOutputs(chatId, userId, agentId, produced);
                }
            }
        } catch (Exception e) {
            log.warn("产出文件收集失败, chatId={}: {}", chatId, e.getMessage());
        }
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        }
        return Math.max(1, bytes / 1024) + " KB";
    }

    /** 冷启动水合：agent_message 展示行 → 请求历史 + 任务清单（游客/无记录时空历史） */
    private ConversationHydrator.Hydrated hydrate() {
        return hydrateConversation(agentMessageService, chatId, userId);
    }

    /** 会话历史水合（轮次与上下文容量估算共用）：游客/无记录/回读失败均回空历史 */
    public static ConversationHydrator.Hydrated hydrateConversation(
            AgentMessageService agentMessageService, String chatId, Long userId) {
        if (userId == null || agentMessageService == null) {
            return new ConversationHydrator.Hydrated(List.of(), List.of());
        }
        try {
            List<AgentMessageDO> rows = agentMessageService.listByConversation(chatId);
            List<ConversationHydrator.DisplayRow> displayRows = rows.stream()
                    .map(row -> new ConversationHydrator.DisplayRow(row.getRole(), row.getBlocks(), row.getPlan()))
                    .toList();
            return ConversationHydrator.hydrate(displayRows);
        } catch (Exception e) {
            log.warn("回读会话历史失败，从空上下文开始: {}", e.getMessage());
            return new ConversationHydrator.Hydrated(List.of(), List.of());
        }
    }

    /** 引擎事件 → SSE 信封 + 日志 */
    private final class BotAgentEvents implements AgentEvents {
        private final ConversationState state;
        private long lastLoggedInput;
        private long lastLoggedOutput;
        private long lastLoggedCached;

        private BotAgentEvents(ConversationState state) {
            this.state = state;
        }

        @Override
        public void thinkingDelta(String delta) {
            channel.thinkingDelta(delta);
        }

        @Override
        public void answerDelta(String delta) {
            channel.answerDelta(delta);
        }

        @Override
        public void toolUse(String id, String name) {
            channel.toolUse(id, name);
        }

        @Override
        public void toolArgs(String id, String delta) {
            channel.toolArgs(id, delta);
        }

        @Override
        public void toolResult(String id, String name, String content, boolean error, long durationMs) {
            channel.toolResult(id, name, content);
        }

        @Override
        public void todosChanged() {
            List<Map<String, Object>> steps = new java.util.ArrayList<>();
            for (TodoItem todo : state.todos()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", steps.size());
                item.put("description", todo.content());
                item.put("status", todo.displayStatus());
                steps.add(item);
            }
            channel.plan(steps);
        }

        @Override
        public void retryScheduled(int attempt, int maxAttempts, String reason) {
            log.warn("模型流瞬态失败，自动重试 {}/{}，chatId={}，reason={}",
                    attempt, maxAttempts, chatId, reason);
            channel.retryScheduled(attempt, maxAttempts, reason);
        }

        @Override
        public void stepFinished(int step, long inputTokens, long outputTokens, long cachedInputTokens,
                                 boolean cachedInputKnown,
                                 List<com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput> toolCalls) {
            logUsageDelta(inputTokens - lastLoggedInput, outputTokens - lastLoggedOutput,
                    cachedInputKnown ? cachedInputTokens - lastLoggedCached : null);
            lastLoggedInput = inputTokens;
            lastLoggedOutput = outputTokens;
            lastLoggedCached = cachedInputTokens;
            // 每步增量落库：崩溃/被杀时刷新页面能看到已流出的部分过程
            channel.flushAssistantWip();
        }

        @Override
        public void compacted(String summary) {
            // 压缩边界是独立消息行（compact 块，前端收折展示），不再附加说明文字
            channel.persistCompact(summary);
        }

        @Override
        public void question(String id, List<Map<String, Object>> questions) {
            channel.question(id, questions);
        }

        @Override
        public void questionAnswered(String id, List<Map<String, Object>> answers) {
            channel.questionAnswered(id, answers);
        }

        private void logUsageDelta(long inputDelta, long outputDelta, Long cachedDelta) {
            // 游客不记用量：agent_usage_log.user_id NOT NULL，游客 userId=null 插入必失败
            if (agentUsageLogService == null || userId == null
                    || (inputDelta <= 0 && outputDelta <= 0 && (cachedDelta == null || cachedDelta <= 0))) {
                return;
            }
            AgentUsageLog usageLog = new AgentUsageLog();
            usageLog.setAgentId(agentId);
            usageLog.setUserId(userId);
            usageLog.setConversationId(parseConversationId());
            usageLog.setModel(modelName);
            usageLog.setStatus(1);
            usageLog.setCreateTime(new Date());
            usageLog.setInputTokens((int) Math.max(0, inputDelta));
            usageLog.setOutputTokens((int) Math.max(0, outputDelta));
            // 上游未回传缓存明细时保持 NULL（命中率按未知处理，不误报 0）
            usageLog.setCachedTokens(cachedDelta == null ? null
                    : (int) Math.min(Integer.MAX_VALUE, Math.max(0, cachedDelta)));
            agentUsageLogService.logUsage(usageLog);
        }
    }

    /**
     * 工具调用审计钩子（afterToolCall）：工具调用日志从引擎事件通道解耦为插件。
     * 注册表在每次工具执行（含失败/超时/子代理）后回调，真实状态/耗时/结果在此落库。
     */
    private final class ToolAuditHook implements AgentHooks {

        /** 工具日志的结果预览上限（output_result 为 text 列，完整结果已在 agent_message.blocks） */
        private static final int RESULT_LOG_CAP = 2_000;

        @Override
        public void afterToolCall(ToolCall call, com.mio.ai.framework.zagent.tools.ToolRegistry.Executed result,
                                  com.mio.ai.framework.zagent.tools.ToolContext context) {
            if (toolCallLogService == null) {
                return;
            }
            try {
                ToolCallLog toolCallLog = new ToolCallLog();
                toolCallLog.setAgentId(agentId);
                toolCallLog.setUserId(userId);
                toolCallLog.setConversationId(parseConversationId());
                toolCallLog.setToolId(0L);
                toolCallLog.setName(call.name());
                toolCallLog.setInputParams(validJsonOrNull(call.argumentsJson()));
                toolCallLog.setStatus(resolveToolStatus(result.error(), result.content()));
                toolCallLog.setExecutionTime((int) Math.min(Integer.MAX_VALUE,
                        Math.max(0, result.durationMs())));
                String content = result.content();
                if (result.error()) {
                    toolCallLog.setErrorMsg(content == null ? "" : content);
                } else if (content != null && content.length() > RESULT_LOG_CAP) {
                    toolCallLog.setOutputResult(content.substring(0, RESULT_LOG_CAP)
                            + "\n[...truncated at " + RESULT_LOG_CAP + " chars...]");
                } else {
                    toolCallLog.setOutputResult(content);
                }
                toolCallLogService.logToolCall(toolCallLog);
            } catch (Exception ignored) {
                // 审计失败不影响工具链
            }
        }

        /** 表字典：1-成功 0-失败 2-超时（超时信封文案来自 ToolRegistry 的超时分支） */
        private int resolveToolStatus(boolean error, String content) {
            if (!error) {
                return 1;
            }
            return content != null && content.contains("exceeded timeout") ? 2 : 0;
        }

        /** input_params 是 JSON 列：模型参数理论上恒为 JSON，畸形时退 null 保落库 */
        private String validJsonOrNull(String args) {
            if (args == null || args.isBlank()) {
                return null;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode ignore =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(args);
                return args;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 群成员展示回调：成员执行以平等消息呈现——开始/回答流/结束分别落 member 块与事件，
     * 用户在消息流里直接看到各成员的作答（不经子代理工具行）。
     */
    private final class GroupMemberPresenter implements com.mio.ai.framework.zagent.subagent.MemberPresenter {
        @Override
        public void started(String agentId, long memberId, String name, String icon) {
            channel.memberStart(agentId, memberId, name, icon);
        }

        @Override
        public void delta(String agentId, String delta) {
            channel.memberDelta(agentId, delta);
        }

        @Override
        public void finished(String agentId, boolean failed) {
            channel.memberDone(agentId, failed);
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

    /** 每日用量累计（游客跳过，统计失败不影响主流程——service 内部已兜底） */
    private void accumulateDailyStats(AgentEngine engine, long durationMs, int calls) {
        if (userStatsDailyService == null || userId == null) {
            return;
        }
        userStatsDailyService.accumulate(userId,
                engine != null ? engine.inputTokens() : 0,
                engine != null ? engine.outputTokens() : 0,
                calls, durationMs);
    }

    /** 群成员委派用量落库：成员 token 以成员 agentId 记一行（群聊统计按实际调用的智能体拆分） */
    private void logMemberUsage(long memberAgentId, long inputTokens, long outputTokens, boolean failed) {
        if (agentUsageLogService == null || userId == null
                || (inputTokens <= 0 && outputTokens <= 0)) {
            return;
        }
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(memberAgentId);
        usageLog.setUserId(userId);
        usageLog.setConversationId(parseConversationId());
        usageLog.setModel(modelName);
        usageLog.setStatus(failed ? 0 : 1);
        usageLog.setCreateTime(new Date());
        usageLog.setInputTokens((int) Math.min(Integer.MAX_VALUE, Math.max(0, inputTokens)));
        usageLog.setOutputTokens((int) Math.min(Integer.MAX_VALUE, Math.max(0, outputTokens)));
        agentUsageLogService.logUsage(usageLog);
    }
}
