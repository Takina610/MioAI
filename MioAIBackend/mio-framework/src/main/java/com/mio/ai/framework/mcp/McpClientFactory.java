package com.mio.ai.framework.mcp;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 客户端工厂：把 mcpServers 单个服务节点统一构造成已 initialize 的 McpSyncClient。
 * <p>支持三种传输：
 * <ul>
 *   <li>Streamable HTTP（type=http/streamable-http，或 AUTO 时优先尝试，2026 年起的主流协议）</li>
 *   <li>SSE（type=sse，或 url 以 /sse 结尾；deprecated 但仍被大量旧服务使用）</li>
 *   <li>STDIO（command/args/env，本地子进程；Windows 下 npx 等无扩展名命令自动解析为 .cmd/.exe）</li>
 * </ul>
 * <p>支持配置中的 headers（Authorization 等认证头）；AUTO 模式在 Streamable 初始化失败时回退 SSE。
 */
@Component
@Slf4j
public class McpClientFactory {

    private static final Set<String> STREAMABLE_TYPES = Set.of(
            "http", "streamable-http", "streamable_http", "streamablehttp", "streamable http");
    private static final String TYPE_SSE = "sse";

    /** STDIO 子进程 stderr 尾部保留行数（仅用于拼进错误消息） */
    private static final int STDERR_TAIL_LINES = 10;

    private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    /**
     * 结构校验（不发起连接）：返回错误信息，null 表示结构可用
     */
    public String validateStructure(String config) {
        if (config == null || config.isBlank()) {
            return "配置不能为空";
        }
        JSONObject configJson;
        try {
            configJson = JSONUtil.parseObj(config);
        } catch (Exception e) {
            return "配置不是有效JSON: " + e.getMessage();
        }
        JSONObject mcpServers = configJson.getJSONObject("mcpServers");
        if (mcpServers == null || mcpServers.isEmpty()) {
            return "配置中未找到 mcpServers 节点";
        }
        String serverName = mcpServers.keySet().iterator().next();
        JSONObject serverConfig;
        try {
            serverConfig = mcpServers.getJSONObject(serverName);
        } catch (Exception e) {
            return "服务器节点必须是JSON对象";
        }
        if (serverConfig == null) {
            return "服务器配置为空";
        }
        String url = serverConfig.getStr("url");
        String command = serverConfig.getStr("command");
        if ((url == null || url.isBlank()) && (command == null || command.isBlank())) {
            return "服务器配置必须包含 url(HTTP/SSE) 或 command(STDIO)";
        }
        return null;
    }

    /**
     * 按单个服务节点创建并初始化客户端，失败抛出异常（含连接原因）
     */
    public McpClientHandle createSyncClient(String clientName, JSONObject serverConfig, Duration requestTimeout) {
        return createSyncClient(clientName, serverConfig, requestTimeout, Duration.ofSeconds(20));
    }

    /**
     * @param initializationTimeout initialize 握手预算：SDK 默认 20s 且对启动失败/连接拒绝也照等不误，
     *                              校验场景传入更短预算让失败尽快暴露
     */
    public McpClientHandle createSyncClient(String clientName, JSONObject serverConfig, Duration requestTimeout,
                                            Duration initializationTimeout) {
        String url = serverConfig.getStr("url");
        String command = serverConfig.getStr("command");
        if (url != null && !url.isBlank()) {
            return createHttpClient(clientName, url, serverConfig, requestTimeout, initializationTimeout);
        }
        if (command != null && !command.isBlank()) {
            return createStdioClient(clientName, serverConfig, requestTimeout, initializationTimeout);
        }
        throw new IllegalArgumentException("配置必须包含 url(HTTP/SSE) 或 command(STDIO)");
    }

    /**
     * 已初始化的客户端及其握手结果（InitializeResult 在 2.0 SDK 中只能从 initialize() 拿到）
     */
    public record McpClientHandle(McpSyncClient client, McpSchema.InitializeResult initResult)
            implements AutoCloseable {
        @Override
        public void close() {
            client.close();
        }
    }

    private McpClientHandle createHttpClient(String clientName, String url, JSONObject serverConfig,
                                             Duration requestTimeout, Duration initializationTimeout) {
        Map<String, String> headers = parseStringMap(serverConfig.getJSONObject("headers"));
        McpSyncHttpClientRequestCustomizer headerCustomizer = (builder, method, uri, body, context) ->
                headers.forEach(builder::header);

        String type = normalizeType(serverConfig.getStr("type"));
        String path = extractPath(url);
        String baseUri = extractBaseUri(url);

        // 明确 SSE、或未写 type 但路径以 /sse 结尾：直接走 SSE 传输
        if (TYPE_SSE.equals(type) || (type == null && path.endsWith("/sse"))) {
            return buildSseClient(clientName, baseUri, path, headerCustomizer, requestTimeout, initializationTimeout);
        }
        // 明确 streamable：只走 Streamable HTTP
        if (type != null) {
            return buildStreamableClient(clientName, baseUri, path, headerCustomizer, requestTimeout,
                    initializationTimeout);
        }
        // AUTO：先尝试 Streamable HTTP（当前主流），失败回退 SSE（兼容旧服务）；
        // 认证类失败换传输层也不会好，直接抛出省掉一次注定失败的重试
        try {
            return buildStreamableClient(clientName, baseUri, path, headerCustomizer, requestTimeout,
                    initializationTimeout);
        } catch (Exception streamableError) {
            if (isAuthFailure(streamableError)) {
                throw streamableError;
            }
            log.info("Streamable HTTP 初始化失败（{}），回退 SSE: {}", streamableError.getMessage(), url);
            return buildSseClient(clientName, baseUri, path, headerCustomizer, requestTimeout, initializationTimeout);
        }
    }

    /** 仅用于跳过无意义的 SSE 回退，错误分类不依赖此判断 */
    private static boolean isAuthFailure(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("401") || msg.contains("403") || msg.contains("unauthorized");
    }

    /**
     * SSE 连接地址：baseUri 取 origin，sseEndpoint 取完整路径（SDK 用 URI.resolve 拼接，
     * 前导 / 端点整体替换路径，恰好拼出完整 url）；路径为空时保持默认 /sse
     */
    private McpClientHandle buildSseClient(String clientName, String baseUri, String path,
                                           McpSyncHttpClientRequestCustomizer customizer, Duration requestTimeout,
                                           Duration initializationTimeout) {
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(baseUri);
        if (!path.isEmpty()) {
            builder.sseEndpoint(path);
        }
        builder.httpRequestCustomizer(customizer);
        return initClient(clientName, builder.build(), requestTimeout, initializationTimeout);
    }

    /**
     * Streamable HTTP 连接地址：baseUri 取 origin，endpoint 取完整路径（前导 / 在 URI.resolve 下整体替换）
     */
    private McpClientHandle buildStreamableClient(String clientName, String baseUri, String path,
                                                  McpSyncHttpClientRequestCustomizer customizer, Duration requestTimeout,
                                                  Duration initializationTimeout) {
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(baseUri);
        if (!path.isEmpty()) {
            builder.endpoint(path);
        }
        builder.httpRequestCustomizer(customizer);
        return initClient(clientName, builder.build(), requestTimeout, initializationTimeout);
    }

    private McpClientHandle createStdioClient(String clientName, JSONObject serverConfig, Duration requestTimeout,
                                              Duration initializationTimeout) {
        String rawCommand = serverConfig.getStr("command");
        String command = McpCommandResolver.resolve(rawCommand);
        // 启动前预检：SDK 对"可执行文件不存在"不报错而是等满初始化超时，必须自己秒级失败
        if (!McpCommandResolver.resolvable(command)) {
            throw new McpProcessStartException(
                    "找不到可执行文件 " + rawCommand + "，请确认已安装并位于 PATH 中");
        }
        JSONArray argsArray = serverConfig.getJSONArray("args");
        List<String> args = argsArray != null ? argsArray.toList(String.class) : List.of();
        // env 值一律转字符串：数字/布尔值直接 toBean(Map) 会在子进程启动时才炸出 ClassCastException
        Map<String, String> env = parseStringMap(serverConfig.getJSONObject("env"));

        StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(command)
                        .args(args)
                        .env(env)
                        .build(),
                jsonMapper
        );
        // 环形收集 stderr 尾部：启动失败/握手失败时子进程真实报错在这里（zcode stderr tail 对位）
        Deque<String> stderrTail = new ArrayDeque<>(STDERR_TAIL_LINES);
        transport.setStdErrorHandler(line -> appendStderr(stderrTail, line));
        try {
            return initClient(clientName, transport, requestTimeout, initializationTimeout);
        } catch (Exception e) {
            String tail = stderrExcerpt(stderrTail);
            if (!tail.isEmpty()) {
                throw new IllegalStateException(e.getMessage() + "｜子进程 stderr: " + tail, e);
            }
            throw e;
        }
    }

    private static void appendStderr(Deque<String> tail, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        synchronized (tail) {
            if (tail.size() >= STDERR_TAIL_LINES) {
                tail.pollFirst();
            }
            tail.addLast(line.length() > 200 ? line.substring(0, 200) + "..." : line);
        }
    }

    private static String stderrExcerpt(Deque<String> tail) {
        synchronized (tail) {
            return String.join(" ⏎ ", tail);
        }
    }

    private McpClientHandle initClient(String clientName, McpClientTransport transport, Duration requestTimeout,
                                       Duration initializationTimeout) {
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation(clientName, "1.0.0"))
                .capabilities(McpSchema.ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                .requestTimeout(requestTimeout)
                .initializationTimeout(initializationTimeout)
                .build();
        McpSchema.InitializeResult initResult;
        try {
            initResult = client.initialize();
        } catch (Exception e) {
            closeQuietly(client);
            throw e;
        }
        return new McpClientHandle(client, initResult);
    }

    private void closeQuietly(McpSyncClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.debug("关闭初始化失败的 MCP 客户端异常: {}", e.getMessage());
        }
    }

    /** headers/env 节点转字符串 Map（值一律 toString）；供 HTTP 传输与错误探测共用 */
    public static Map<String, String> parseStringMap(JSONObject obj) {
        if (obj == null || obj.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : obj.keySet()) {
            Object value = obj.get(key);
            if (value != null) {
                map.put(key, String.valueOf(value));
            }
        }
        return map;
    }

    private String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String lower = type.trim().toLowerCase();
        if (lower.isEmpty()) {
            return null;
        }
        if (TYPE_SSE.equals(lower)) {
            return TYPE_SSE;
        }
        return STREAMABLE_TYPES.contains(lower) ? "streamable" : lower;
    }

    private String extractPath(String url) {
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getRawPath();
            return path == null ? "" : path;
        } catch (Exception e) {
            return "";
        }
    }

    private String extractBaseUri(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getRawAuthority() == null) {
                return url.trim();
            }
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (Exception e) {
            return url.trim();
        }
    }
}
