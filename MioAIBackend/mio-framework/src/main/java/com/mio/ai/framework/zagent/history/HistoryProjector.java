package com.mio.ai.framework.zagent.history;

import com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput;
import com.mio.ai.framework.zagent.tools.ToolMedia;
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

    public static List<Message> project(List<Message> systemPrefix, List<ConversationEntry> entries) {
        List<Message> messages = new ArrayList<>(systemPrefix);
        List<ToolResponseMessage.ToolResponse> pendingToolResults = new ArrayList<>();
        List<ToolMedia> pendingMedia = new ArrayList<>();
        Set<String> mediaToolNames = new LinkedHashSet<>();
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
                            calls.add(new AssistantMessage.ToolCall(
                                    input.id() == null ? "" : input.id(), "function",
                                    input.name(), input.arguments() == null ? "{}" : input.arguments()));
                        }
                        messages.add(AssistantMessage.builder()
                                .content(entry.text == null ? "" : entry.text)
                                .toolCalls(calls)
                                .build());
                    }
                }
                case TOOL -> {
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
            for (ToolMedia m : pendingMedia) {
                parts.add(Media.builder()
                        .mimeType(MimeType.valueOf(m.mimeType()))
                        .data(Base64.getDecoder().decode(m.base64()))
                        .build());
            }
            messages.add(UserMessage.builder().text(intro).media(parts).build());
            pendingMedia.clear();
            mediaToolNames.clear();
        }
    }
}
