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

    /**
     * 取链上最有信息量的消息：优先识别已知网络异常类型（Windows 的 java.net.http 对
     * 拒绝连接可能抛无消息的 ClosedChannelException，必须按类型穿透）；否则取最外层有效消息，
     * 一路走到最深反而会拿到无信息量的类名
     */
    private static String rootMessage(Throwable e) {
        for (Throwable cur = e; cur != null; cur = cur.getCause() == cur ? null : cur.getCause()) {
            if (cur instanceof java.net.ConnectException) {
                return messageOr(cur, "连接被拒绝（服务未启动或端口不正确）");
            }
            if (cur instanceof java.net.UnknownHostException) {
                return messageOr(cur, "域名无法解析");
            }
            if (cur instanceof java.net.SocketTimeoutException) {
                return messageOr(cur, "连接超时");
            }
        }
        Throwable cur = e;
        while (cur != null) {
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                return cur.getMessage();
            }
            if (cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
        }
        Throwable deepest = e;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        return deepest.getClass().getSimpleName();
    }

    private static String messageOr(Throwable t, String fallback) {
        return t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : fallback;
    }
}
