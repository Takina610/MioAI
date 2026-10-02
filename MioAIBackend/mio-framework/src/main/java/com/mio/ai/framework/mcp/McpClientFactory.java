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
        String url = serverConfig.getStr("url");
        String command = serverConfig.getStr("command");
        if (url != null && !url.isBlank()) {
            return createHttpClient(clientName, url, serverConfig, requestTimeout);
        }
        if (command != null && !command.isBlank()) {
            return createStdioClient(clientName, serverConfig, requestTimeout);
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
                                             Duration requestTimeout) {
        Map<String, String> headers = parseStringMap(serverConfig.getJSONObject("headers"));
        McpSyncHttpClientRequestCustomizer headerCustomizer = (builder, method, uri, body, context) ->
                headers.forEach(builder::header);

        String type = normalizeType(serverConfig.getStr("type"));
        String path = extractPath(url);
        String baseUri = extractBaseUri(url);

        // 明确 SSE、或未写 type 但路径以 /sse 结尾：直接走 SSE 传输
        if (TYPE_SSE.equals(type) || (type == null && path.endsWith("/sse"))) {
            return buildSseClient(clientName, baseUri, path, headerCustomizer, requestTimeout);
        }
        // 明确 streamable：只走 Streamable HTTP
        if (type != null) {
            return buildStreamableClient(clientName, baseUri, path, headerCustomizer, requestTimeout);
        }
        // AUTO：先尝试 Streamable HTTP（当前主流），失败回退 SSE（兼容旧服务）
        try {
            return buildStreamableClient(clientName, baseUri, path, headerCustomizer, requestTimeout);
        } catch (Exception streamableError) {
            log.info("Streamable HTTP 初始化失败（{}），回退 SSE: {}", streamableError.getMessage(), url);
            return buildSseClient(clientName, baseUri, path, headerCustomizer, requestTimeout);
        }
    }

    /**
     * SSE 连接地址：baseUri 取 origin，sseEndpoint 取完整路径（SDK 用 URI.resolve 拼接，
     * 前导 / 端点整体替换路径，恰好拼出完整 url）；路径为空时保持默认 /sse
     */
    private McpClientHandle buildSseClient(String clientName, String baseUri, String path,
                                           McpSyncHttpClientRequestCustomizer customizer, Duration requestTimeout) {
        HttpClientSseClientTransport.Builder builder = HttpClientSseClientTransport.builder(baseUri);
        if (!path.isEmpty()) {
            builder.sseEndpoint(path);
        }
        builder.httpRequestCustomizer(customizer);
        return initClient(clientName, builder.build(), requestTimeout);
    }

    /**
     * Streamable HTTP 连接地址：baseUri 取 origin，endpoint 取完整路径（前导 / 在 URI.resolve 下整体替换）
     */
    private McpClientHandle buildStreamableClient(String clientName, String baseUri, String path,
                                                  McpSyncHttpClientRequestCustomizer customizer, Duration requestTimeout) {
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(baseUri);
        if (!path.isEmpty()) {
            builder.endpoint(path);
        }
        builder.httpRequestCustomizer(customizer);
        return initClient(clientName, builder.build(), requestTimeout);
    }

    private McpClientHandle createStdioClient(String clientName, JSONObject serverConfig, Duration requestTimeout) {
        String command = McpCommandResolver.resolve(serverConfig.getStr("command"));
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
        return initClient(clientName, transport, requestTimeout);
    }

    private McpClientHandle initClient(String clientName, McpClientTransport transport, Duration requestTimeout) {
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation(clientName, "1.0.0"))
                .capabilities(McpSchema.ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                .requestTimeout(requestTimeout)
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

    private Map<String, String> parseStringMap(JSONObject obj) {
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
