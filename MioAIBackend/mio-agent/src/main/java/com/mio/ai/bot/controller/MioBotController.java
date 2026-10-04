package com.mio.ai.bot.controller;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.bot.agent.MioBot;
import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.model.vo.ChatVO;
import com.mio.ai.bot.repository.ChatHistoryRepository;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.service.BotResourceService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.framework.sandbox.SandboxSession;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.resource.service.security.AccessGuardService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private AgentMessageService agentMessageService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SandboxSession sandboxSession;

    /** Agent 单轮步数上限（zcode 风格宽松默认，防失控而非限制任务长度） */
    @org.springframework.beans.factory.annotation.Value("${mio.ai.agent.max-steps:100}")
    private int agentMaxSteps;

    /** 单次模型流式调用超时（须不小于本地反代的总预算，否则先被后端掐断） */
    @org.springframework.beans.factory.annotation.Value("${mio.ai.agent.stream-timeout-seconds:1800}")
    private long agentStreamTimeoutSeconds;

    /** 思考强度白名单：与 openai-java ReasoningEffort 枚举一致，前端输入框下方可调 */
    private static final java.util.Set<String> REASONING_EFFORTS =
            java.util.Set.of("minimal", "low", "medium", "high", "xhigh", "max", "none");

    /** 正在执行中的会话：断线自动重连/双击等重复请求直接拒绝，防止同一轮任务被重复执行 */
    private static final java.util.Set<String> ACTIVE_CHATS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @GetMapping("/bot/chat")
    public SseEmitter chat(@RequestParam @NotBlank @Size(max = 64) String chatId,
                           @RequestParam @NotBlank @Size(max = 20000) String content,
                           @RequestParam(required = false) Long agentId,
                           @RequestParam(required = false) String token,
                           @RequestParam(defaultValue = "false") boolean skipUserPersist,
                           @RequestParam(required = false) String reasoningEffort) {
        Long userId = StrUtil.isBlank(token) ? null : redisComponent.getUserId(token);
        long resolvedAgentId = agentId != null ? agentId : MioBot.AGENT_ID;

        if (!ACTIVE_CHATS.add(chatId)) {
            return emitSingleReply("当前会话有正在进行的任务，请等待完成后再发送（可刷新页面查看进度）。");
        }

        // MioBot 游客可用；自定义智能体要求登录且有权使用（所有者/公开已发布）
        String customSystemPrompt = null;
        if (resolvedAgentId != MioBot.AGENT_ID) {
            Agent agent = accessGuardService.checkAgentUsable(resolvedAgentId, userId);
            customSystemPrompt = agent.getSystemPrompt();
        }

        // 会话记录：仅登录用户落库（游客会话不产生列表项）；重新生成时用户消息已在历史里，跳过
        if (userId != null && !skipUserPersist) {
            ChatVO chatVO = new ChatVO();
            chatVO.setChatId(chatId);
            chatVO.setMessage(content);
            chatVO.setAgentId(resolvedAgentId);
            chatVO.setUserId(userId);
            chatHistoryRepository.save(chatVO);
        }

        if (containsSensitiveWord(content)) {
            ACTIVE_CHATS.remove(chatId);
            return emitSingleReply(SENSITIVE_REPLY);
        }

        ToolCallback[] mcpTools = botResourceService.getMcpToolCallbacks(resolvedAgentId);
        String knowledgeContext = botResourceService.buildKnowledgeContext(resolvedAgentId, userId, content);

        String effort = reasoningEffort != null && REASONING_EFFORTS.contains(reasoningEffort)
                ? reasoningEffort : null;
        MioBot mioBot = new MioBot(chatModel, jdbcChatMemory, commonTools,
                List.of(mcpTools), agentUsageLogService, toolCallLogService, agentMessageService,
                chatId, userId, resolvedAgentId, customSystemPrompt, sandboxSession, agentMaxSteps,
                agentStreamTimeoutSeconds, effort);
        // 任务真正结束（含异常）时解除会话占用
        mioBot.setOnFinish(() -> ACTIVE_CHATS.remove(chatId));
        return mioBot.run(content, knowledgeContext, !skipUserPersist);
    }

    /**
     * 截断会话历史（编辑消息/重新生成共用）：保留 seq &le; keepThroughSeq 的展示消息，
     * 删除其后的行，并按剩余行重建 chat_memory（清除旧记忆后重放）。
     * 返回保留部分最后一条用户消息内容，供重新生成场景原样重发。
     */
    @PostMapping("/bot/truncate/{conversationId}")
    public BaseResponse<Map<String, Object>> truncate(@PathVariable @NotBlank String conversationId,
                                                      @RequestParam long keepThroughSeq,
                                                      jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        ChatConversationDO conversation = chatHistoryRepository.getChatByConversationId(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (conversation.getUserId() == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该会话");
        }

        agentMessageService.deleteAfterSeq(conversationId, keepThroughSeq);
        List<Message> replayed = rebuildMemory(conversationId);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("keptMessages", replayed.size());
        for (int i = replayed.size() - 1; i >= 0; i--) {
            if (replayed.get(i) instanceof UserMessage user) {
                result.put("userContent", user.getText());
                break;
            }
        }
        return ResultUtils.success(result);
    }

    /** 清空旧记忆后按展示消息重放（user 原文 / assistant 文本块拼接），保证记忆与截断后的展示一致 */
    private List<Message> rebuildMemory(String conversationId) {
        List<Message> replayed = new ArrayList<>();
        for (AgentMessageDO row : agentMessageService.listByConversation(conversationId)) {
            if ("user".equals(row.getRole())) {
                replayed.add(new UserMessage(blockText(row.getBlocks())));
            } else if ("assistant".equals(row.getRole())) {
                replayed.add(new AssistantMessage(blockText(row.getBlocks())));
            }
        }
        jdbcChatMemory.clear(conversationId);
        for (Message message : replayed) {
            jdbcChatMemory.add(conversationId, message);
        }
        return replayed;
    }

    /** 从展示 blocks JSON 提取纯文本（text 块拼接；旧数据无 blocks 时回退空串） */
    private String blockText(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return "";
        }
        try {
            List<Map<String, Object>> blocks = JacksonUtil.readValue(blocksJson, List.class);
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> block : blocks) {
                if ("text".equals(block.get("type")) && block.get("text") != null) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(String.valueOf(block.get("text")));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 会话的完整消息（含工具调用/任务清单等工作过程），刷新/回看时原样还原。
     * 仅会话所有者可读；老会话无记录时返回空数组（前端回退旧文本接口）。
     */
    @GetMapping("/bot/messages/{conversationId}")
    public BaseResponse<List<Map<String, Object>>> getMessages(
            @PathVariable @NotBlank String conversationId,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        com.mio.ai.bot.model.entity.ChatConversationDO conversation =
                chatHistoryRepository.getChatByConversationId(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (conversation.getUserId() == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该会话");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentMessageDO message : agentMessageService.listByConversation(conversationId)) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("role", message.getRole());
            row.put("seq", message.getSeq());
            row.put("blocks", parseJson(message.getBlocks()));
            row.put("plan", parseJson(message.getPlan()));
            row.put("durationMs", message.getDurationMs());
            row.put("createTime", message.getCreateTime());
            rows.add(row);
        }
        return ResultUtils.success(rows);
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JacksonUtil.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
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
