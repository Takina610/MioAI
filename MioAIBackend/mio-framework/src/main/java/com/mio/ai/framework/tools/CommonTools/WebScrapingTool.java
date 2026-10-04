package com.mio.ai.framework.tools.CommonTools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * @author: Takina
 * @date: 2026/3/31 18:48
 * @description: 网页抓取工具。提取正文文本（剔除脚本/导航等噪音）而非原始 HTML，
 * 避免整页源码灌进模型上下文（参考 pi/opencode 的工具输出截断策略）。
 */
@Component
public class WebScrapingTool {

    /** 正文最长保留字符数：超出保留前段并标注总长 */
    private static final int MAX_CONTENT_CHARS = 9000;

    private static final int TIMEOUT_MS = 15000;

    @Tool(description = "抓取网页并提取正文纯文本（自动剔除导航、脚本等噪音，保留标题）。用于阅读搜索结果里的具体文章或文档")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页URL") String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "Chrome/124.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(4 * 1024 * 1024)
                    .get();
            return extractText(document);
        } catch (Exception e) {
            return "抓取网页错误: " + e.getMessage();
        }
    }

    /** 结构化正文：标题 + 摘要 + 正文（换行归一化、长度截断） */
    static String extractText(Document document) {
        document.select("script, style, noscript, iframe, svg, form, nav, header, footer, aside").remove();
        // 块级元素转换行，text() 才不会把段落挤成一行
        document.select("br").append("\\n");
        document.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, blockquote, pre").prepend("\\n");

        String title = blankToEmpty(document.title());
        var metaDescription = document.selectFirst("meta[name=description]");
        String description = metaDescription != null ? blankToEmpty(metaDescription.attr("content")) : "";
        String body = document.body() != null ? document.body().text().replace("\\n", "\n") : "";
        body = body.strip().replaceAll("\n{3,}", "\n\n");

        StringJoiner sj = new StringJoiner("\n");
        sj.add("标题: " + (title.isEmpty() ? "(无)" : title));
        if (!description.isEmpty()) {
            sj.add("摘要: " + description);
        }
        sj.add("正文:");
        if (body.length() > MAX_CONTENT_CHARS) {
            sj.add(body.substring(0, MAX_CONTENT_CHARS));
            sj.add("…[正文共 " + body.length() + " 字符，已截断保留前 " + MAX_CONTENT_CHARS + " 字符]");
        } else {
            sj.add(body.isEmpty() ? "(未提取到正文文本，可能是纯脚本渲染页面)" : body);
        }
        return sj.toString();
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.strip();
    }
}
