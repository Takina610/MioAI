package com.mio.ai.common.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

/**
 * XSS 过滤工具类
 * 基于 jsoup 实现输入内容的危险标签和属性过滤
 */
public class XssUtils {

    /**
     * 允许富文本（如 Markdown 渲染后的 HTML）使用的标签白名单
     */
    private static final Safelist RICH_TEXT_ALLOWLIST = Safelist.basicWithImages()
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https", "data")
            .addTags("h1", "h2", "h3", "h4", "h5", "h6",
                    "pre", "code", "blockquote",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "hr", "br", "p", "div", "span", "sup", "sub")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addAttributes("th", "colspan", "rowspan", "align")
            .addAttributes("td", "colspan", "rowspan", "align")
            .addAttributes("div", "class")
            .addAttributes("span", "class");

    /**
     * 严格模式：只允许纯文本级别的基本标签
     */
    private static final Safelist STRICT_ALLOWLIST = Safelist.basic()
            .addProtocols("a", "href", "http", "https", "mailto");

    /**
     * 清理富文本 HTML（保留 Markdown 常用标签）
     * 同时处理 Markdown 链接中的 javascript: 协议
     */
    public static String cleanRichText(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String cleaned = Jsoup.clean(html, "", RICH_TEXT_ALLOWLIST,
                new Document.OutputSettings().prettyPrint(false));
        // 将 HTML 实体编码还原，避免双编码
        cleaned = Entities.unescape(cleaned);
        // 清理 Markdown 链接中的 javascript: 协议
        cleaned = cleaned.replaceAll("(?i)\\[([^\\]]*)\\]\\(javascript:[^)]*\\)", "[$1](#)");
        return cleaned;
    }

    /**
     * 严格清理：只保留最基础的安全标签
     */
    public static String cleanStrict(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, "", STRICT_ALLOWLIST,
                new Document.OutputSettings().prettyPrint(false));
    }

    /**
     * 完全转义：将所有 HTML 标签转为实体，用于纯文本展示
     */
    public static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /**
     * 快速检测是否包含潜在 XSS 攻击向量
     */
    public static boolean containsXssRisk(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        String lower = content.toLowerCase();
        return lower.contains("<script")
                || lower.contains("javascript:")
                || lower.contains("onerror=")
                || lower.contains("onload=")
                || lower.contains("onmouseover=")
                || lower.contains("onclick=")
                || lower.contains("<iframe")
                || lower.contains("<object")
                || lower.contains("<embed")
                || lower.contains("<form");
    }
}
