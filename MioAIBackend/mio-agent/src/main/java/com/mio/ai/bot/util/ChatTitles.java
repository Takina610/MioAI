package com.mio.ai.bot.util;

/**
 * 会话标题工具：模型生成标题的清洗与会话创建时的首条消息兜底标题。
 * <p>兜底规则与前端 useChatSessions.deriveTitle 保持一致——标题永远取得到值：
 * 会话创建即落兜底标题，模型精化失败时保留兜底，不再出现"新对话"占位。
 */
public final class ChatTitles {

    /** 兜底标题最大长度（超出截断加省略号） */
    private static final int FALLBACK_MAX_LEN = 24;

    /** 模型生成标题的最大长度（提示词要求 ≤15 字，此处放宽截断防超长） */
    private static final int MODEL_MAX_LEN = 30;

    private ChatTitles() {
    }

    /** 清洗模型生成的标题：去 Markdown 符号/包裹引号/首尾空白，超长截断；无效返回 null */
    public static String cleanModelTitle(String title) {
        String cleaned = clean(title);
        if (cleaned == null) {
            return null;
        }
        return truncate(cleaned, MODEL_MAX_LEN);
    }

    /** 首条消息兜底标题：取首个非空行清洗截断；无效返回 null */
    public static String fallbackTitle(String firstMessage) {
        if (firstMessage == null) {
            return null;
        }
        for (String line : firstMessage.split("\r?\n")) {
            String cleaned = clean(line);
            if (cleaned != null) {
                return truncate(cleaned, FALLBACK_MAX_LEN);
            }
        }
        return null;
    }

    /** 去 Markdown 强调符/标题符/包裹引号与首尾空白；全无效返回 null */
    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.strip()
                .replaceAll("^[#>\\s]+", "")
                .replaceAll("[*_~`]+", "")
                .replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
                .replaceAll("\\s+", " ")
                .strip();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}
