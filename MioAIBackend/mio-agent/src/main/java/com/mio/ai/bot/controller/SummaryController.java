package com.mio.ai.bot.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.bot.model.dto.ChatMessageRequest;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.util.ChatTitles;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话标题生成：首轮对话后由前端触发。
 * <p>标题永不失效——生成带一次重试；模型失败时回退到库内标题（会话创建时已落
 * 首条消息兜底标题，见 ChatHistoryRepositoryImpl），不再返回"新对话"占位。
 */
@Slf4j
@RestController
public class SummaryController {

    /** 模型生成失败后的重试间隔 */
    private static final long RETRY_DELAY_MS = 1_500;
    /** 送入模型的对话样本上限（标题生成不需要长文，控制 token 与时延） */
    private static final int SAMPLE_MAX_CHARS = 2_000;

    @Resource(name = "summaryChatClient")
    ChatClient chatClient;

    @Resource
    ChatConversationDOMapper chatConversationDOMapper;

    /**
     * 输入一段对话文本，返回一句总结标题；生成失败回退库内/首条消息标题。
     */
    @PostMapping("/summary")
    public BaseResponse<String> generateTitle(@Valid @RequestBody ChatMessageRequest chatMessageRequest) {
        String conversationId = chatMessageRequest.getConversationId();
        String currentTitle = currentTitle(conversationId, chatMessageRequest.getAgentId());

        String title = generateWithRetry(chatMessageRequest.getContent());
        if (title == null) {
            title = StrUtil.isNotBlank(currentTitle)
                    ? currentTitle : StrUtil.emptyIfNull(ChatTitles.fallbackTitle(chatMessageRequest.getContent()));
            log.warn("标题生成失败，使用兜底标题, conversationId={}, title={}", conversationId, title);
            return ResultUtils.success(title);
        }

        persistTitle(conversationId, chatMessageRequest.getAgentId(), title);
        return ResultUtils.success(title);
    }

    /** 模型生成 + 一次重试；全部失败返回 null */
    private String generateWithRetry(String content) {
        String sample = content.length() > SAMPLE_MAX_CHARS
                ? content.substring(0, SAMPLE_MAX_CHARS) : content;
        String prompt = """
            请你给下面这段对话，生成一个简短标题，要求：
            1. 一句话
            2. 不超过15个字
            3. 直白、概括核心内容
            4. 纯文本输出，不要使用任何 Markdown 格式符号（如 **、*、#、`）

            对话内容：
            %s
            """.formatted(sample);
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String title = ChatTitles.cleanModelTitle(
                        chatClient.prompt().user(prompt).call().content());
                if (title != null) {
                    return title;
                }
                log.warn("标题生成返回空结果, attempt={}", attempt);
            } catch (Exception e) {
                log.warn("标题生成模型调用失败, attempt={}: {}", attempt, e.getMessage());
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private String currentTitle(String conversationId, Long agentId) {
        ChatConversationDO conversation = chatConversationDOMapper.selectOne(
                new QueryWrapper<ChatConversationDO>()
                        .eq("conversation_id", conversationId)
                        .eq("agent_id", agentId));
        return conversation == null ? null : conversation.getTitle();
    }

    private void persistTitle(String conversationId, Long agentId, String title) {
        try {
            UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda()
                    .eq(ChatConversationDO::getConversationId, conversationId)
                    .eq(ChatConversationDO::getAgentId, agentId)
                    .set(ChatConversationDO::getTitle, title);
            chatConversationDOMapper.update(updateWrapper);
        } catch (Exception e) {
            log.warn("标题落库失败, conversationId={}: {}", conversationId, e.getMessage());
        }
    }
}
