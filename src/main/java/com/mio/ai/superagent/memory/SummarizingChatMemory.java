package com.mio.ai.superagent.memory;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 摘要记忆（Summarizing Memory）
 * <p>在固定窗口记忆之上增加一层"会话摘要"：当历史消息数超过阈值时，
 * 把较早的消息压缩成一条摘要 SystemMessage 注入上下文，仅保留最近 N 条完整消息。
 * 这样长对话既不会突破窗口丢失全部早期信息，也不会把上下文撑爆。
 * <p>摘要按会话缓存，只有历史发生变化时才重新调用模型压缩。
 */
@Slf4j
public class SummarizingChatMemory implements ChatMemory {

    private static final String SUMMARY_PREFIX = "【历史对话摘要】";
    private static final int MAX_SUMMARY_INPUT_MESSAGES = 100;

    private final ChatMemory delegate;
    private final ChatClient summaryChatClient;
    private final int summaryThreshold;
    private final int keepRecentMessages;

    /**
     * 会话摘要缓存：conversationId -> (摘要文本, 摘要覆盖的最后一条消息时间戳)
     */
    private final Map<String, CachedSummary> summaryCache = new ConcurrentHashMap<>();

    public SummarizingChatMemory(ChatMemory delegate,
                                 ChatClient summaryChatClient,
                                 int summaryThreshold,
                                 int keepRecentMessages) {
        this.delegate = delegate;
        this.summaryChatClient = summaryChatClient;
        this.summaryThreshold = Math.max(summaryThreshold, keepRecentMessages + 10);
        this.keepRecentMessages = Math.max(keepRecentMessages, 4);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        delegate.add(conversationId, messages);
    }

    @Override
    public void add(String conversationId, Message message) {
        delegate.add(conversationId, message);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = delegate.get(conversationId);
        if (all == null || all.size() <= summaryThreshold) {
            return all;
        }
        return withSummary(conversationId, all);
    }

    @Override
    public void clear(String conversationId) {
        summaryCache.remove(conversationId);
        delegate.clear(conversationId);
    }

    /**
     * 把 [0, size-keepRecent) 的旧消息压缩为摘要，拼在最近消息前
     */
    private List<Message> withSummary(String conversationId, List<Message> all) {
        int boundary = all.size() - keepRecentMessages;
        List<Message> older = all.subList(0, boundary);
        List<Message> recent = new ArrayList<>(all.subList(boundary, all.size()));

        Message last = all.get(all.size() - 1);
        long lastTimestamp = extractTimestamp(last);
        CachedSummary cached = summaryCache.get(conversationId);
        String summary;
        if (cached != null && cached.coveredUpTo == lastTimestamp && StrUtil.isNotBlank(cached.summary)) {
            summary = cached.summary;
        } else {
            summary = summarize(older);
            if (StrUtil.isNotBlank(summary)) {
                summaryCache.put(conversationId, new CachedSummary(summary, lastTimestamp));
            } else {
                // 压缩失败时退回固定窗口行为，只保留最近消息
                log.warn("会话 {} 摘要生成失败，退回固定窗口记忆", conversationId);
                return recent;
            }
        }
        List<Message> result = new ArrayList<>(recent.size() + 1);
        result.add(new SystemMessage(SUMMARY_PREFIX + "\n" + summary));
        result.addAll(recent);
        return result;
    }

    private String summarize(List<Message> older) {
        if (older.isEmpty()) {
            return null;
        }
        List<Message> input = older.size() > MAX_SUMMARY_INPUT_MESSAGES
                ? older.subList(older.size() - MAX_SUMMARY_INPUT_MESSAGES, older.size())
                : older;
        StringBuilder transcript = new StringBuilder();
        for (Message m : input) {
            String text = m.getText();
            if (StrUtil.isBlank(text)) {
                continue;
            }
            String truncated = text.length() > 800 ? text.substring(0, 800) + "..." : text;
            transcript.append(m.getMessageType()).append(": ").append(truncated).append("\n");
        }
        if (transcript.isEmpty()) {
            return null;
        }
        try {
            String prompt = """
                    请把以下一段人与AI助手的对话历史压缩成一份简洁的中文摘要，用于后续对话的上下文。
                    要求：
                    1. 保留用户的身份信息、偏好、关键决定与未完成的事项；
                    2. 保留对话中出现过的重要事实（数字、名称、结论）；
                    3. 使用条目式短句，总长度不超过300字；
                    4. 只输出摘要内容，不要任何解释。

                    对话历史：
                    """ + transcript;
            String summary = summaryChatClient.prompt()
                    .user(new UserMessage(prompt).getText())
                    .call()
                    .content();
            return StrUtil.isNotBlank(summary) ? summary.trim() : null;
        } catch (Exception e) {
            log.warn("生成会话摘要失败: {}", e.getMessage());
            return null;
        }
    }

    private long extractTimestamp(Message message) {
        try {
            var metadata = message.getMetadata();
            Object ts = metadata != null ? metadata.get("timestamp") : null;
            if (ts instanceof Number number) {
                return number.longValue();
            }
        } catch (Exception ignored) {
        }
        // 无时间戳元数据时用内容哈希做变化标记
        String text = message.getText();
        return text != null ? text.hashCode() : 0L;
    }

    private record CachedSummary(String summary, long coveredUpTo) {
    }
}
