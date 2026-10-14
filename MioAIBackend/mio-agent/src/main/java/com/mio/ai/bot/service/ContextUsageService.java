package com.mio.ai.bot.service;

import com.mio.ai.bot.agent.MioBot;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.context.ContextBuilder;
import com.mio.ai.framework.zagent.history.ConversationEntry;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.subagent.SubagentLauncher;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import com.mio.ai.framework.zagent.tools.ToolRegistry;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import com.mio.ai.resource.mapper.log.AgentUsageLogMapper;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.Skill;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文容量估算：与真实轮次同源的水合/上下文构建/工具注册管线做一次"干跑"，
 * 按类别（消息/系统工具/MCP 工具/系统提示词/技能/其他）汇总近似 token 数。
 * <p>估算是静止状态的下一请求快照：知识库上下文随用户消息实时生成，不在此预算内；
 * token 口径与压缩器一致（ConversationEntry.estimateText：CJK 1 token/字，其余 /4）。
 */
@Slf4j
@Service
public class ContextUsageService {

    private final ToolsetFactory toolsetFactory;
    private final BotResourceService botResourceService;
    private final AgentMessageService agentMessageService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final AgentUsageLogMapper agentUsageLogMapper;

    /** 引擎使用的模型名（与 yml 的 chat.model 一致，环境提示段按此渲染） */
    @Value("${spring.ai.openai.chat.model:}")
    private String engineModelName;

    /** 上下文窗口（token）：与引擎压缩阈值同源 */
    @Value("${mio.ai.agent.context-window-tokens:200000}")
    private long contextWindowTokens;

    public ContextUsageService(ToolsetFactory toolsetFactory,
                               BotResourceService botResourceService,
                               AgentMessageService agentMessageService,
                               ChatHistoryRepository chatHistoryRepository,
                               AgentUsageLogMapper agentUsageLogMapper) {
        this.toolsetFactory = toolsetFactory;
        this.botResourceService = botResourceService;
        this.agentMessageService = agentMessageService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.agentUsageLogMapper = agentUsageLogMapper;
    }

    /**
     * 估算会话下一请求的上下文占用。
     *
     * @param userId             归属用户（游客 null）
     * @param agentId            智能体（自定义智能体的调用方需先过使用权校验）
     * @param customSystemPrompt 自定义智能体的身份提示词
     * @param windowOverride     当前模型的上下文窗口（token，&gt;0 生效）：与轮次引擎同源解析，
     *                           指示器按所选模型如实显示；缺省用配置窗口
     */
    public Map<String, Object> estimate(String chatId, Long userId, long agentId, String customSystemPrompt,
                                        long windowOverride) {
        checkOwnership(chatId, userId);
        long windowTokens = windowOverride > 0 ? windowOverride : contextWindowTokens;

        // 会话历史：与轮次水合同一管线（游客/无记录为空历史）
        List<ConversationEntry> entries =
                MioBot.hydrateConversation(agentMessageService, chatId, userId).entries();
        long messagesTokens = entries.stream().mapToLong(ConversationEntry::estimateTokens).sum();

        // 系统段 + 附件段：知识库上下文仅随消息生成，静止状态传 null；技能段单独可识别
        List<Skill> skills = botResourceService.assembleSkills(agentId);
        String skillsSection = SkillRuntime.renderSection(skills);
        SandboxFs fs = toolsetFactory.sandboxFs();
        ContextBuilder contextBuilder = new ContextBuilder(
                customSystemPrompt, null, skillsSection, fs, engineModelName,
                userId != null ? agentId : null);
        ContextBuilder.Built built = contextBuilder.build();
        long systemPromptTokens = built.systemMessages().stream()
                .mapToLong(message -> ConversationEntry.estimateText(message.getText())).sum();
        long attachmentTokens = built.attachmentBodies().stream()
                .mapToLong(ConversationEntry::estimateText).sum();
        long skillsTokens = skillsSection == null ? 0 : ConversationEntry.estimateText(skillsSection);
        long otherTokens = Math.max(0, attachmentTokens - skillsTokens);

        // 工具 schema：内置工具与真实轮次同一注册逻辑（含 Agent/AskUserQuestion）；
        // MCP 工具直接按装配出的 callback 定义估算
        long systemToolsTokens = 0;
        ToolRegistry registry = toolsetFactory.build(chatId, new ConversationState(), List.of(),
                null, noopLauncher(), noopEvents(), null);
        try {
            for (ToolCallback callback : registry.asToolCallbacks()) {
                systemToolsTokens += estimateDefinition(callback.getToolDefinition());
            }
        } finally {
            registry.shutdown();
        }
        long mcpTokens = 0;
        for (ToolCallback callback : botResourceService.assembleMcpTools(agentId).callbacks()) {
            mcpTokens += estimateDefinition(callback.getToolDefinition());
        }

        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("messages", messagesTokens);
        categories.put("systemTools", systemToolsTokens);
        categories.put("mcpTools", mcpTokens);
        categories.put("systemPrompt", systemPromptTokens);
        categories.put("skills", skillsTokens);
        categories.put("other", otherTokens);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowTokens", windowTokens);
        result.put("usedTokens", messagesTokens + systemToolsTokens + mcpTokens
                + systemPromptTokens + skillsTokens + otherTokens);
        result.put("categories", categories);
        result.put("cacheRate", cacheRate(chatId));
        return result;
    }

    /**
     * 平均缓存命中率：会话累计 缓存读取/输入 token（zcode 同口径的加权平均，非逐次平均）。
     * 会话无带缓存明细的用量记录时返回 null（前端隐藏该行）。
     */
    private Double cacheRate(String chatId) {
        Long conversationId = parseConversationId(chatId);
        if (conversationId == null) {
            return null;
        }
        QueryWrapper<AgentUsageLog> query = new QueryWrapper<>();
        query.select("COALESCE(SUM(input_tokens), 0) AS input_sum",
                        "COALESCE(SUM(cached_tokens), 0) AS cached_sum")
                .eq("conversation_id", conversationId)
                .isNotNull("cached_tokens");
        List<Map<String, Object>> rows = agentUsageLogMapper.selectMaps(query);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        long inputSum = ((Number) rows.get(0).getOrDefault("input_sum", 0)).longValue();
        long cachedSum = ((Number) rows.get(0).getOrDefault("cached_sum", 0)).longValue();
        return inputSum > 0 ? cachedSum / (double) inputSum : null;
    }

    /** 会话 id 为数字型时转 conversation_id（临时/游客会话无用量记录，回 null） */
    private Long parseConversationId(String chatId) {
        try {
            return Long.valueOf(chatId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 登录用户的会话归属校验：查得到会话且不属于本人即拒绝（token 数也只对自己的会话开放） */
    private void checkOwnership(String chatId, Long userId) {
        if (userId == null) {
            return;
        }
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(chatId);
        if (conversation != null && !userId.equals(conversation.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该会话");
        }
    }

    /** 工具定义的近似 token：名称 + 描述 + 参数 schema（OpenAI function 包裹开销忽略） */
    private long estimateDefinition(ToolDefinition definition) {
        if (definition == null) {
            return 0;
        }
        return ConversationEntry.estimateText(String.valueOf(definition.name())
                + definition.description()
                + definition.inputSchema());
    }

    /** 估算干跑的占位启动器：仅为了让注册表含 Agent 工具（与真实轮次一致），永不执行 */
    private SubagentLauncher noopLauncher() {
        return (subagentType, description, prompt, background) -> null;
    }

    /** 估算干跑的空事件通道：仅为了让注册表含 AskUserQuestion（与真实轮次一致） */
    private AgentEvents noopEvents() {
        return new AgentEvents() {
            @Override
            public void thinkingDelta(String delta) {
            }

            @Override
            public void answerDelta(String delta) {
            }

            @Override
            public void toolUse(String id, String name) {
            }

            @Override
            public void toolArgs(String id, String delta) {
            }

            @Override
            public void toolResult(String id, String name, String content, boolean error, long durationMs) {
            }

            @Override
            public void todosChanged() {
            }

            @Override
            public void retryScheduled(int attempt, int maxAttempts, String reason) {
            }

            @Override
            public void stepFinished(int step, long inputTokens, long outputTokens, long cachedInputTokens,
                                     boolean cachedInputKnown,
                                     List<ConversationEntry.ToolCallInput> toolCalls) {
            }

            @Override
            public void compacted(String summary) {
            }
        };
    }
}
