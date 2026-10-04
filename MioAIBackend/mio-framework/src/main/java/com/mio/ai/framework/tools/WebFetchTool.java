package com.mio.ai.framework.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.StringJoiner;

/**
 * 通用网页抓取原语（参考 zcode 的 WebFetch / pi 的 fetch）：任意 URL → 干净正文文本。
 * 与联网搜索、bash(curl) 组合即可覆盖"查任何资料"的需求——不需要领域专用工具。
 */
@Component
public class WebFetchTool {

    /** 正文最长保留字符数：超出保留前段并标注总长 */
    private static final int MAX_CONTENT_CHARS = 9000;

    private static final int TIMEOUT_MS = 15000;

    @Tool(description = "抓取任意网页并提取正文纯文本（自动剔除导航/脚本噪音，保留标题与摘要），过长截断。"
            + "阅读搜索结果里的文章、文档，或访问已知 URL/API 时使用；需要请求头或参数的 API 调用可改用 runCommand 的 curl")
    public String fetchUrl(@ToolParam(description = "要抓取的网页或接口 URL") String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "Chrome/124.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(4 * 1024 * 1024)
                    .ignoreContentType(true)
                    .get();
            return extractText(document);
        } catch (Exception e) {
            return "抓取网页错误: " + e.getMessage();
        }
    }

    /** 结构化正文：标题 + 摘要 + 正文（换行归一化、长度截断）；JSON 响应原样返回 */
    static String extractText(Document document) {
        String body = document.body() != null ? document.body().text() : "";
        String contentType = document.selectFirst("meta[http-equiv=content-type]") != null
                ? document.selectFirst("meta[http-equiv=content-type]").attr("content") : "";
        if (contentType.contains("json") || body.trim().startsWith("{") || body.trim().startsWith("[")) {
            return body.length() > MAX_CONTENT_CHARS * 2 ? body.substring(0, MAX_CONTENT_CHARS * 2) : body;
        }
        document.select("script, style, noscript, iframe, svg, form, nav, header, footer, aside").remove();
        document.select("br").append("\\n");
        document.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, blockquote, pre").prepend("\\n");

        String title = document.title() == null ? "" : document.title().strip();
        var metaDescription = document.selectFirst("meta[name=description]");
        String description = metaDescription != null ? metaDescription.attr("content") : "";
        description = description == null ? "" : description.strip();
        body = document.body() != null ? document.body().text().replace("\\n", "\n") : "";
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
}
