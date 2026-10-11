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

    /**
     * 条目的近似 token 估算（zcode：字符数/4，工具入参计入）。
     * <p>中文感知修正：CJK 字符约 0.6~0.75 token/字（qwen/glm 系词表），按 1 token/字
     * 高估计价；其余字符维持 /4。纯 /4 会把中文历史低估约 2 倍，导致自动压缩永不触发。
     */
    public long estimateTokens() {
        long cjkChars = 0;
        long otherChars = 0;
        long[] counted = countChars(text);
        cjkChars += counted[0];
        otherChars += counted[1];
        counted = countChars(content);
        cjkChars += counted[0];
        otherChars += counted[1];
        if (toolCalls != null) {
            for (ToolCallInput call : toolCalls) {
                counted = countChars(call.arguments());
                cjkChars += counted[0];
                otherChars += counted[1];
            }
        }
        if (media != null) {
            // 图片 token 按 base64 实长 × 0.125 估算（zcode READ_IMAGE_TOKEN_TO_BASE64_CHAR_RATIO）
            for (ToolMedia m : media) {
                otherChars += m.base64() == null ? 0 : Math.round(m.base64().length() * 0.125) * 4;
            }
        }
        return cjkChars + otherChars / 4;
    }

    /** 任意文本的近似 token 估算（与条目同口径：CJK 1 token/字，其余 /4），供上下文容量估算复用 */
    public static long estimateText(String text) {
        long[] counted = countChars(text);
        return counted[0] + counted[1] / 4;
    }

    private static long[] countChars(String text) {
        if (text == null || text.isEmpty()) {
            return new long[]{0, 0};
        }
        long cjk = 0;
        long other = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                cjk++;
            } else {
                other++;
            }
        }
        return new long[]{cjk, other};
    }

    /** CJK 统意文字区（含扩展A/兼容/全角标点）：中文场景一个字≈一个 token */
    private static boolean isCjk(char c) {
        return (c >= 0x2E80 && c <= 0x9FFF)      // 部首补充 ~ CJK 统一表意文字
                || (c >= 0xF900 && c <= 0xFAFF)   // CJK 兼容表意
                || (c >= 0xFF00 && c <= 0xFFEF);  // 全角标点与假名
    }
}
