package com.mio.ai.customagent.service.mcp.impl;

import cn.hutool.json.JSONObject;
import com.mio.ai.customagent.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验失败错误还原：SDK 会把 HTTP 真实错误吞成无 cause 的 RuntimeException，
 * 这里验证探针能把服务端真实状态码/响应体带回错误消息
 */
class McpValidateErrorClassificationTest {

    private HttpServer server;

    private final McpValidateServiceImpl service = new McpValidateServiceImpl(new McpClientFactory());

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void httpAuthErrorSurfacedFromProbe() throws Exception {
        startServer(401, "missing required Authorization header");
        McpValidateResultVO r = validateHttp();
        assertEquals("AUTH_FAILED", r.getErrorType());
        assertTrue(r.getErrorMessage().contains("401"), r.getErrorMessage());
        assertTrue(r.getErrorMessage().contains("Authorization"), r.getErrorMessage());
    }

    @Test
    void http404ReportedAsEndpointProblem() throws Exception {
        startServer(404, "not found");
        McpValidateResultVO r = validateHttp();
        assertEquals("CONNECTION_FAILED", r.getErrorType());
        assertTrue(r.getErrorMessage().contains("404"), r.getErrorMessage());
    }

    @Test
    void http405HintsSseTypeMismatch() throws Exception {
        startServer(405, "Method Not Allowed");
        McpValidateResultVO r = validateHttp();
        assertEquals("CONNECTION_FAILED", r.getErrorType());
        assertTrue(r.getErrorMessage().contains("405"), r.getErrorMessage());
        assertTrue(r.getErrorMessage().contains("type=sse"), r.getErrorMessage());
    }

    @Test
    void httpReachableButHandshakeFailed() throws Exception {
        startServer(200, "{\"jsonrpc\":\"2.0\",\"result\":{}}");
        McpValidateResultVO r = validateHttp();
        assertEquals("UNKNOWN", r.getErrorType());
        assertTrue(r.getErrorMessage().contains("握手失败"), r.getErrorMessage());
    }

    @Test
    void stdioCauseChainSurfaced() {
        Exception e = new RuntimeException("Client failed to initialize",
                new java.io.IOException("Cannot run program \"nope\": CreateProcess error=2"));
        JSONObject config = new JSONObject();
        config.set("command", "nope");
        McpValidateResultVO r = service.buildErrorResult(e, config);
        assertEquals("CONNECTION_FAILED", r.getErrorType());
        assertTrue(r.getErrorMessage().contains("Cannot run program"), r.getErrorMessage());
    }

    @Test
    void timeoutKeywordKept() {
        JSONObject config = new JSONObject();
        config.set("command", "slow-server");
        McpValidateResultVO r = service.buildErrorResult(new RuntimeException("Request timed out"), config);
        assertEquals("TIMEOUT", r.getErrorType());
        assertTrue(r.getErrorMessage().contains("timed out"), r.getErrorMessage());
    }

    private McpValidateResultVO validateHttp() {
        JSONObject config = new JSONObject();
        config.set("type", "http");
        config.set("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        return service.buildErrorResult(
                new RuntimeException("Client failed to initialize by explicit API call"), config);
    }

    private void startServer(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }
}
