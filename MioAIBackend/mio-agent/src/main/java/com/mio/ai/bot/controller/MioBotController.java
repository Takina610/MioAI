package com.mio.ai.bot.controller;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.agent.MioBot;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.vo.ChatVO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.bot.service.BotResourceService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.resource.service.security.AccessGuardService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 统一对话入口：MioBot（系统内置智能体，id=1）与用户自定义智能体共用同一套流式 Agent 引擎。
 * <p>MioBot 游客可对话（不落会话记录）；自定义智能体要求登录并校验使用权（所有者/公开已发布），
 * 使用其绑定的 MCP 工具与知识库，身份提示词取 agent.system_prompt。
 */
@Validated
@RestController
public class MioBotController {

    /**
     * 敏感词拦截（原各 ChatClient 上的 ChineseSafeGuardAdvisor 收敛到这里）：
     * 命中时以一条完整回复结束，不进入 Agent 循环
     */
    private static final List<String> SENSITIVE_WORDS = List.of("公务员", "政府", "政治", "暴力");

    private static final String SENSITIVE_REPLY = "抱歉，您的问题涉及敏感内容，我无法回答。请换一种方式提问或讨论其他话题。";

    @Resource
    private ToolCallback[] commonTools;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    @Qualifier("jdbcChatMemory")
    private ChatMemory jdbcChatMemory;

    @Autowired
    private ChatHistoryRepository chatHistoryRepository;

    @Autowired
    private RedisComponent redisComponent;

    @Autowired
    private BotResourceService botResourceService;

    @Autowired
    private AccessGuardService accessGuardService;

    @Autowired
    private AgentUsageLogService agentUsageLogService;

    @Autowired
    private ToolCallLogService toolCallLogService;

    @GetMapping("/bot/chat")
    public SseEmitter chat(@RequestParam @NotBlank @Size(max = 64) String chatId,
                           @RequestParam @NotBlank @Size(max = 20000) String content,
                           @RequestParam(required = false) Long agentId,
                           @RequestParam(required = false) String token) {
        Long userId = StrUtil.isBlank(token) ? null : redisComponent.getUserId(token);
        long resolvedAgentId = agentId != null ? agentId : MioBot.AGENT_ID;

        // MioBot 游客可用；自定义智能体要求登录且有权使用（所有者/公开已发布）
        String customSystemPrompt = null;
        if (resolvedAgentId != MioBot.AGENT_ID) {
            Agent agent = accessGuardService.checkAgentUsable(resolvedAgentId, userId);
            customSystemPrompt = agent.getSystemPrompt();
        }

        // 会话记录：仅登录用户落库（游客会话不产生列表项）
        if (userId != null) {
            ChatVO chatVO = new ChatVO();
            chatVO.setChatId(chatId);
            chatVO.setMessage(content);
            chatVO.setAgentId(resolvedAgentId);
            chatVO.setUserId(userId);
            chatHistoryRepository.save(chatVO);
        }

        if (containsSensitiveWord(content)) {
            return emitSingleReply(SENSITIVE_REPLY);
        }

        ToolCallback[] mcpTools = botResourceService.getMcpToolCallbacks(resolvedAgentId);
        String knowledgeContext = botResourceService.buildKnowledgeContext(resolvedAgentId, userId, content);

        MioBot mioBot = new MioBot(chatModel, jdbcChatMemory, commonTools,
                List.of(mcpTools), agentUsageLogService, toolCallLogService,
                chatId, userId, resolvedAgentId, customSystemPrompt);
        return mioBot.run(content, knowledgeContext);
    }

    private boolean containsSensitiveWord(String content) {
        return SENSITIVE_WORDS.stream().anyMatch(content::contains);
    }

    /** 敏感词命中的单条完整回复（answer 整段 + done） */
    private SseEmitter emitSingleReply(String reply) {
        SseEmitter emitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        CompletableFuture.runAsync(() -> {
            SseStreams.sendTyped(emitter, SseChunk.delta("answer", reply).fields(), 1);
            SseStreams.sendTyped(emitter, SseChunk.done().fields(), 2);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
            }
        });
        return emitter;
    }
}
