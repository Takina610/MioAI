package com.mio.ai.framework.mcp;

import cn.hutool.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * MCP HTTP 端点错误探测：SDK 2.0 的 LifecycleInitializer 会把 HTTP 层真实错误
 * （401/404/5xx 等）吞成无 cause 的 RuntimeException("Client failed to initialize by
 * explicit API call")，异常文本无从还原真实原因。校验失败后由这里按同样方式发一个
 * initialize 请求，拿回服务端真实状态码与响应体，用于还原错误信息。
 */
public final class McpHttpErrorProbe {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
    private static final int BODY_EXCERPT_MAX = 120;

    private static final String INITIALIZE_BODY = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"mio-ai-validator","version":"1.0.0"}}}""";

    private McpHttpErrorProbe() {
    }

    /**
     * @return statusCode 为 -1 表示网络层就没通，此时 networkError 为根因消息
     */
    public static ProbeResult probe(JSONObject serverConfig) {
        String url = serverConfig.getStr("url");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(PROBE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_BODY));
            McpClientFactory.parseStringMap(serverConfig.getJSONObject("headers"))
                    .forEach(builder::header);
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(PROBE_TIMEOUT)
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new ProbeResult(resp.statusCode(), excerpt(resp.body()), null);
        } catch (Exception e) {
            return new ProbeResult(-1, null, rootMessage(e));
        }
    }

    public record ProbeResult(int statusCode, String bodyExcerpt, String networkError) {
        public boolean networkFailed() {
            return statusCode < 0;
        }
    }

    /** 响应体压成单行并截断，只用于放进错误消息 */
    private static String excerpt(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String flattened = body.replaceAll("\\s+", " ").trim();
        return flattened.length() > BODY_EXCERPT_MAX
                ? flattened.substring(0, BODY_EXCERPT_MAX) + "..."
                : flattened;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }
}
