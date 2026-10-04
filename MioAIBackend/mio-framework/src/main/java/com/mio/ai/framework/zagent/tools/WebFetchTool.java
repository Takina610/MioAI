package com.mio.ai.framework.zagent.tools;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.WEB_FETCH;

/**
 * WebFetch 工具（zcode handlers/webfetch.ts 的对位移植）：
 * 抓取 URL → 干净正文 → 用快速模型按 prompt 作答；HTTPS 升级、15 分钟缓存、
 * 跨域重定向与 HTTP 错误文案对齐。
 */
final class WebFetchTool {

    private static final int TIMEOUT_MS = 60_000;
    private static final long CACHE_TTL_MS = 15 * 60_000;
    private static final int MAX_CONTENT_CHARS = 100_000;

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private final ChatModel chatModel;

    record CacheEntry(long fetchedAt, String content) {
    }

    WebFetchTool(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "url":{"type":"string","format":"uri","description":"The URL to fetch content from"},
                "prompt":{"type":"string","description":"The prompt to run on the fetched content"}},
                "required":["url","prompt"]}""";
        return ToolEntry.ofReadOnly("WebFetch", WEB_FETCH, schema, this::execute);
    }

    String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String rawUrl = Args.str(input, "url");
        String prompt = Args.str(input, "prompt");
        if (rawUrl == null || prompt == null) {
            throw new ToolUseFailure(1, "Both url and prompt are required.");
        }
        String url = rawUrl.startsWith("http://") ? "https://" + rawUrl.substring("http://".length()) : rawUrl;

        CacheEntry cached = CACHE.get(url);
        String content;
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < CACHE_TTL_MS) {
            content = cached.content();
        } else {
            content = fetch(url);
            if (content == null) {
                return "The server returned an error for " + url
                        + ". The response body was not retrieved. If this URL requires authentication, "
                        + "use an authenticated tool (e.g. `gh` for GitHub, or an MCP-provided fetch tool) "
                        + "instead of WebFetch.";
            }
            if (CACHE.size() > 64) {
                CACHE.clear();
            }
            CACHE.put(url, new CacheEntry(System.currentTimeMillis(), content));
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS) + "\n[content truncated]";
        }
        return answer(url, content, prompt);
    }

    private String fetch(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(10 * 1024 * 1024)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .execute();
            Document document = response.parse();
            document.select("script, style, noscript, iframe, svg, form").remove();
            document.select("br").append("\\n");
            document.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, blockquote, pre").prepend("\\n");
            String body = document.body() != null ? document.body().text().replace("\\n", "\n") : "";
            String title = document.title() == null ? "" : document.title();
            return "# " + title.strip() + "\n\n" + body.strip().replaceAll("\n{3,}", "\n\n");
        } catch (Exception e) {
            return null;
        }
    }

    /** zcode：用小快模型对正文按 prompt 作答 */
    private String answer(String url, String content, String prompt) {
        try {
            String system = "You are a content extraction assistant. You are given the text content of a web page "
                    + "and a prompt. Answer the prompt using only the page content. "
                    + "If the page does not contain the answer, say so. Reply in the language of the prompt.";
            String user = "URL: " + url + "\n\nPage content:\n" + content + "\n\nPrompt: " + prompt;
            Prompt request = new Prompt(List.of(new SystemMessage(system), new UserMessage(user)));
            return chatModel.call(request).getResult().getOutput().getText();
        } catch (Exception e) {
            // 模型通道不可用时退化为返回正文前段（保底可用）
            return "Page content (model summarization unavailable):\n\n"
                    + content.substring(0, Math.min(content.length(), 4000));
        }
    }
}
