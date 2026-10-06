package com.mio.ai.framework.zagent.history;

import com.mio.ai.framework.zagent.tools.ToolMedia;

import java.util.List;

/**
 * 对话历史的原子条目（zcode RuntimeMessageEntry 的对位移植）。
 * <p>四类：真实用户输入、助手消息（可携带工具调用）、工具结果、
 * system-reminder 附件（以 user 角色渲染、用 &lt;system-reminder&gt; 包裹）。
 * <p>工具结果可携带媒体（Read 图片）：仅存活于本轮内存历史，投影时拆成
 * 后置 user 消息（zcode tool-result-media-projection）；水合重建的文本历史不含媒体。
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
    /** 工具结果媒体负载（Read 图片），无媒体为空列表 */
    public final List<ToolMedia> media;

    private ConversationEntry(Kind kind, String text, String source, List<ToolCallInput> toolCalls,
                              String toolCallId, String toolName, String content, boolean error,
                              List<ToolMedia> media) {
        this.kind = kind;
        this.text = text;
        this.source = source;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.content = content;
        this.error = error;
        this.media = media == null ? List.of() : media;
    }

    public static ConversationEntry user(String text) {
        return new ConversationEntry(Kind.USER, text, null, null, null, null, null, false, null);
    }

    public static ConversationEntry reminder(String source, String body) {
        return new ConversationEntry(Kind.REMINDER, body, source, null, null, null, null, false, null);
    }

    public static ConversationEntry assistant(String text, List<ToolCallInput> toolCalls) {
        return new ConversationEntry(Kind.ASSISTANT, text == null ? "" : text, null,
                toolCalls == null ? List.of() : toolCalls, null, null, null, false, null);
    }

    public static ConversationEntry toolResult(String toolCallId, String toolName, String content, boolean error) {
        return toolResult(toolCallId, toolName, content, error, List.of());
    }

    public static ConversationEntry toolResult(String toolCallId, String toolName, String content,
                                               boolean error, List<ToolMedia> media) {
        return new ConversationEntry(Kind.TOOL, null, null, null, toolCallId, toolName, content, error, media);
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
        if (media != null) {
            // 图片 token 按 base64 实长 × 0.125 估算（zcode READ_IMAGE_TOKEN_TO_BASE64_CHAR_RATIO）
            for (ToolMedia m : media) {
                chars += m.base64() == null ? 0 : Math.round(m.base64().length() * 0.125) * 4;
            }
        }
        return chars / 4;
    }
}
