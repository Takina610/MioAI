package com.mio.ai.framework.zagent.history;

import java.util.List;

/**
 * 对话历史的原子条目（zcode RuntimeMessageEntry 的对位移植）。
 * <p>四类：真实用户输入、助手消息（可携带工具调用）、工具结果、
 * system-reminder 附件（以 user 角色渲染、用 &lt;system-reminder&gt; 包裹）。
 */
public final class ConversationEntry {

    public enum Kind {USER, ASSISTANT, TOOL, REMINDER}

    /** 助手消息携带的工具调用（OpenAI 形状） */
    public record ToolCallInput(String id, String name, String arguments) {
    }

    public final Kind kind;
    /** user/assistant 正文（assistant 可为空串） */
    public final String text;
    /** reminder 的来源标识（todo_reminder / date_change / task_notification / shared_context / compact） */
    public final String source;
    /** assistant 的工具调用列表 */
    public final List<ToolCallInput> toolCalls;
    /** 工具结果字段 */
    public final String toolCallId;
    public final String toolName;
    public final String content;
    public final boolean error;

    private ConversationEntry(Kind kind, String text, String source, List<ToolCallInput> toolCalls,
                              String toolCallId, String toolName, String content, boolean error) {
        this.kind = kind;
        this.text = text;
        this.source = source;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.content = content;
        this.error = error;
    }

    public static ConversationEntry user(String text) {
        return new ConversationEntry(Kind.USER, text, null, null, null, null, null, false);
    }

    public static ConversationEntry reminder(String source, String body) {
        return new ConversationEntry(Kind.REMINDER, body, source, null, null, null, null, false);
    }

    public static ConversationEntry assistant(String text, List<ToolCallInput> toolCalls) {
        return new ConversationEntry(Kind.ASSISTANT, text == null ? "" : text, null,
                toolCalls == null ? List.of() : toolCalls, null, null, null, false);
    }

    public static ConversationEntry toolResult(String toolCallId, String toolName, String content, boolean error) {
        return new ConversationEntry(Kind.TOOL, null, null, null, toolCallId, toolName, content, error);
    }

    /** 条目的近似 token 估算（zcode：字符数/4，工具入参计入） */
    public long estimateTokens() {
        long chars = 0;
        if (text != null) {
            chars += text.length();
        }
        if (content != null) {
            chars += content.length();
        }
        if (toolCalls != null) {
            for (ToolCallInput call : toolCalls) {
                chars += call.arguments() == null ? 0 : call.arguments().length();
            }
        }
        return chars / 4;
    }
}
