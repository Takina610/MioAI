package com.mio.ai.bot.controller;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.agent.MioBot;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.vo.ChatVO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.bot.service.BotResourceService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
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
 * MioBot 对话入口（全站唯一聊天端点）。
 * <p>登录用户按 token 解析身份并落会话记录；游客可对话但不落会话（消息仅在本次连接内可见）。
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
    private AgentUsageLogService agentUsageLogService;

    @Autowired
    private ToolCallLogService toolCallLogService;

    @GetMapping("/bot/chat")
    public SseEmitter chat(@RequestParam @NotBlank @Size(max = 64) String chatId,
                           @RequestParam @NotBlank @Size(max = 20000) String content,
                           @RequestParam(required = false) String token) {
        Long userId = StrUtil.isBlank(token) ? null : redisComponent.getUserId(token);

        // 会话记录：仅登录用户落库（游客会话不产生列表项）
        if (userId != null) {
            ChatVO chatVO = new ChatVO();
            chatVO.setChatId(chatId);
            chatVO.setMessage(content);
            chatVO.setAgentId(MioBot.AGENT_ID);
            chatVO.setUserId(userId);
            chatHistoryRepository.save(chatVO);
        }

        if (containsSensitiveWord(content)) {
            return emitSingleReply(SENSITIVE_REPLY);
        }

        ToolCallback[] mcpTools = botResourceService.getPublicMcpToolCallbacks();
        String knowledgeContext = botResourceService.buildKnowledgeContext(userId, content);

        MioBot mioBot = new MioBot(chatModel, jdbcChatMemory, commonTools,
                List.of(mcpTools), agentUsageLogService, toolCallLogService, chatId, userId);
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
