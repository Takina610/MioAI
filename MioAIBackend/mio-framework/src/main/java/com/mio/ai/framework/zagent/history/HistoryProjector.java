package com.mio.ai.framework.zagent.history;

import com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求投影（zcode provider-request-messages 的对位移植）：
 * 把会话条目渲染为提供方消息序列。
 * <p>system-reminder 附件以 user 角色渲染并用 &lt;system-reminder&gt; 包裹；
 * 连续工具结果合并为一条 ToolResponseMessage；压缩续接消息加前缀说明。
 */
public final class HistoryProjector {

    /** zcode 压缩续接消息前缀 */
    public static final String COMPACT_CONTINUATION_PREFIX =
            "This session is being continued from a previous conversation that ran out of context. "
                    + "The summary below describes the conversation so far. Treat it as background context "
                    + "and continue assisting the user.";

    public static List<Message> project(List<Message> systemPrefix, List<ConversationEntry> entries) {
        List<Message> messages = new ArrayList<>(systemPrefix);
        List<ToolResponseMessage.ToolResponse> pendingToolResults = new ArrayList<>();
        for (ConversationEntry entry : entries) {
            switch (entry.kind) {
                case USER -> {
                    flushToolResults(messages, pendingToolResults);
                    messages.add(new UserMessage(entry.text));
                }
                case REMINDER -> {
                    flushToolResults(messages, pendingToolResults);
                    String body = "compact".equals(entry.source)
                            ? COMPACT_CONTINUATION_PREFIX + "\n\n" + entry.text
                            : entry.text;
                    messages.add(new UserMessage(wrapReminder(body)));
                }
                case ASSISTANT -> {
                    flushToolResults(messages, pendingToolResults);
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
                case TOOL -> pendingToolResults.add(new ToolResponseMessage.ToolResponse(
                        entry.toolCallId, entry.toolName, entry.content == null ? "" : entry.content));
            }
        }
        flushToolResults(messages, pendingToolResults);
        return messages;
    }

    /** zcode wrapSystemReminder：内容两端换行连接 */
    public static String wrapReminder(String body) {
        return "<system-reminder>\n" + body + "\n</system-reminder>";
    }

    private static void flushToolResults(List<Message> messages,
                                         List<ToolResponseMessage.ToolResponse> pending) {
        if (!pending.isEmpty()) {
            messages.add(ToolResponseMessage.builder().responses(new ArrayList<>(pending)).build());
            pending.clear();
        }
    }
}
