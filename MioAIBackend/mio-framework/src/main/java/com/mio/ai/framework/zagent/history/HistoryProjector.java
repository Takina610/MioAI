package com.mio.ai.framework.zagent.history;

import com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput;
import com.mio.ai.framework.zagent.tools.contract.ToolMedia;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 请求投影（zcode provider-request-messages 的对位移植）：
 * 把会话条目渲染为提供方消息序列。
 * <p>system-reminder 附件以 user 角色渲染并用 &lt;system-reminder&gt; 包裹；
 * 连续工具结果合并为一条 ToolResponseMessage；压缩续接消息加前缀说明。
 * <p>工具结果媒体投影（zcode tool-result-media-projection）：OpenAI 系协议的
 * tool role 不携带媒体，图片拆成紧随工具结果的后置 user 消息
 * （"Tool result media from &lt;tool&gt;:" + image part）。
 */
public final class HistoryProjector {

    /** zcode 压缩续接消息前缀 */
    public static final String COMPACT_CONTINUATION_PREFIX =
            "This session is being continued from a previous conversation that ran out of context. "
                    + "The summary below describes the conversation so far. Treat it as background context "
                    + "and continue assisting the user.";

    /** zcode TOOL_RESULT_MEDIA_INTRO_PREFIX */
    private static final String TOOL_RESULT_MEDIA_INTRO_PREFIX = "Tool result media from";

    /** 单张媒体上限（解码后字节）：约 1.4MB base64，覆盖常规截图/缩略图 */
    static final int MAX_MEDIA_BYTES_PER_ITEM = 1024 * 1024;
    /** 单次投影的媒体总量上限：防止多张大图叠加把上下文撑爆 */
    static final int MAX_MEDIA_BYTES_TOTAL = 3 * 1024 * 1024;

    public static List<Message> project(List<Message> systemPrefix, List<ConversationEntry> entries) {
        List<Message> messages = new ArrayList<>(systemPrefix);
        List<ToolResponseMessage.ToolResponse> pendingToolResults = new ArrayList<>();
        List<ToolMedia> pendingMedia = new ArrayList<>();
        Set<String> mediaToolNames = new LinkedHashSet<>();
        // 尚未被任何 assistant tool_call 声明的 tool 结果：提供方要求逐个配对，
        // 孤立结果（压缩/截断/水合边界产生）会让整个请求被 400，这里直接丢弃
        Set<String> declaredToolCallIds = new LinkedHashSet<>();
        for (ConversationEntry entry : entries) {
            switch (entry.kind) {
                case USER -> {
                    flushToolResults(messages, pendingToolResults, pendingMedia, mediaToolNames);
                    messages.add(new UserMessage(entry.text));
                }
                case REMINDER -> {
                    flushToolResults(messages, pendingToolResults, pendingMedia, mediaToolNames);
                    String body = "compact".equals(entry.source)
                            ? COMPACT_CONTINUATION_PREFIX + "\n\n" + entry.text
                            : entry.text;
                    messages.add(new UserMessage(wrapReminder(body)));
                }
                case ASSISTANT -> {
                    flushToolResults(messages, pendingToolResults, pendingMedia, mediaToolNames);
                    if (entry.toolCalls.isEmpty()) {
                        if (!entry.text.isBlank()) {
                            messages.add(new AssistantMessage(entry.text));
                        }
                    } else {
                        List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                        for (ToolCallInput input : entry.toolCalls) {
                            String id = input.id() == null ? "" : input.id();
                            calls.add(new AssistantMessage.ToolCall(id, "function",
                                    input.name(), input.arguments() == null ? "{}" : input.arguments()));
                            declaredToolCallIds.add(id);
                        }
                        messages.add(AssistantMessage.builder()
                                .content(entry.text == null ? "" : entry.text)
                                .toolCalls(calls)
                                .build());
                    }
                }
                case TOOL -> {
                    if (!declaredToolCallIds.contains(entry.toolCallId)) {
                        // 无对应 assistant 调用的孤立工具结果：丢弃，避免上游 400
                        continue;
                    }
                    pendingToolResults.add(new ToolResponseMessage.ToolResponse(
                            entry.toolCallId, entry.toolName, entry.content == null ? "" : entry.content));
                    if (entry.media != null && !entry.media.isEmpty()) {
                        pendingMedia.addAll(entry.media);
                        mediaToolNames.add(entry.toolName);
                    }
                }
            }
        }
        flushToolResults(messages, pendingToolResults, pendingMedia, mediaToolNames);
        return messages;
    }

    /** zcode wrapSystemReminder：内容两端换行连接 */
    public static String wrapReminder(String body) {
        return "<system-reminder>\n" + body + "\n</system-reminder>";
    }

    private static void flushToolResults(List<Message> messages,
                                         List<ToolResponseMessage.ToolResponse> pending,
                                         List<ToolMedia> pendingMedia,
                                         Set<String> mediaToolNames) {
        if (!pending.isEmpty()) {
            messages.add(ToolResponseMessage.builder().responses(new ArrayList<>(pending)).build());
            pending.clear();
        }
        if (!pendingMedia.isEmpty()) {
            String intro = TOOL_RESULT_MEDIA_INTRO_PREFIX + " " + String.join(", ", mediaToolNames) + ":";
            List<Media> parts = new ArrayList<>(pendingMedia.size());
            long totalBytes = 0;
            int skipped = 0;
            for (ToolMedia m : pendingMedia) {
                // 体积上限：媒体会随历史进入后续每一次请求（压缩也会重发），
                // 不设限时几张原图就能把上下文撑爆、请求必然超限
                String base64 = m.base64();
                if (base64 == null || base64.isBlank()) {
                    skipped++;
                    continue;
                }
                long estimated = (long) base64.length() * 3 / 4;
                if (estimated > MAX_MEDIA_BYTES_PER_ITEM
                        || totalBytes + estimated > MAX_MEDIA_BYTES_TOTAL) {
                    skipped++;
                    continue;
                }
                try {
                    byte[] data = Base64.getDecoder().decode(base64);
                    parts.add(Media.builder()
                            .mimeType(MimeType.valueOf(m.mimeType()))
                            .data(data)
                            .build());
                    totalBytes += data.length;
                } catch (Exception malformed) {
                    // 非法 base64：跳过该张，不影响其余媒体
                    skipped++;
                }
            }
            if (skipped > 0) {
                intro += " (" + skipped + " media item(s) omitted: malformed or exceeds size limit)";
            }
            if (!parts.isEmpty()) {
                messages.add(UserMessage.builder().text(intro).media(parts).build());
            }
            pendingMedia.clear();
            mediaToolNames.clear();
        }
    }
}
