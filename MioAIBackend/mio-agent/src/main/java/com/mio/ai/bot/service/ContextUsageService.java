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
import com.mio.ai.resource.model.entity.Skill;
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

    /** 引擎使用的模型名（与 yml 的 chat.model 一致，环境提示段按此渲染） */
    @Value("${spring.ai.openai.chat.model:}")
    private String engineModelName;

    /** 上下文窗口（token）：与引擎压缩阈值同源 */
    @Value("${mio.ai.agent.context-window-tokens:200000}")
    private long contextWindowTokens;

    public ContextUsageService(ToolsetFactory toolsetFactory,
                               BotResourceService botResourceService,
                               AgentMessageService agentMessageService,
                               ChatHistoryRepository chatHistoryRepository) {
        this.toolsetFactory = toolsetFactory;
        this.botResourceService = botResourceService;
        this.agentMessageService = agentMessageService;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    /**
     * 估算会话下一请求的上下文占用。
     *
     * @param userId             归属用户（游客 null）
     * @param agentId            智能体（自定义智能体的调用方需先过使用权校验）
     * @param customSystemPrompt 自定义智能体的身份提示词
     */
    public Map<String, Object> estimate(String chatId, Long userId, long agentId, String customSystemPrompt) {
        checkOwnership(chatId, userId);

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
        result.put("windowTokens", contextWindowTokens);
        result.put("usedTokens", messagesTokens + systemToolsTokens + mcpTokens
                + systemPromptTokens + skillsTokens + otherTokens);
        result.put("categories", categories);
        return result;
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
            public void stepFinished(int step, long inputTokens, long outputTokens,
                                     List<ConversationEntry.ToolCallInput> toolCalls) {
            }

            @Override
            public void compacted(String summary) {
            }
        };
    }
}
