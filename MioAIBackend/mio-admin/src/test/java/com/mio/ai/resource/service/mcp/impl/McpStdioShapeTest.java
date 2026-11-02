package com.mio.ai.resource.service.mcp.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * STDIO 形态判定回归测试：门禁用它区分「存量 STDIO（必须保留可用）」与
 * 「把 HTTP 工具覆盖成 STDIO（必须拦截）」。判错任一方向都会造成
 * 存量工具静默失效，或放行命令执行注入。
 */
class McpStdioShapeTest {

    private static JSONObject server(String json) {
        JSONObject root = JSONUtil.parseObj("{\"mcpServers\":{\"s\":" + json + "}}");
        return root.getJSONObject("mcpServers").getJSONObject("s");
    }

    @Test
    void commandWithoutUrlIsStdio() {
        Assertions.assertTrue(McpClientManagerServiceImpl.isStdio(
                server("{\"command\":\"npx\",\"args\":[\"-y\",\"server\"]}")));
    }

    @Test
    void urlBasedIsNotStdioEvenWithArgs() {
        Assertions.assertFalse(McpClientManagerServiceImpl.isStdio(
                server("{\"type\":\"http\",\"url\":\"https://h.example.com/mcp\",\"args\":[]}")));
        // url 与 command 并存按 HTTP 处理（真实连接也走 url 分支）
        Assertions.assertFalse(McpClientManagerServiceImpl.isStdio(
                server("{\"url\":\"https://h/mcp\",\"command\":\"npx\"}")));
    }

    @Test
    void blankCommandIsNotStdio() {
        Assertions.assertFalse(McpClientManagerServiceImpl.isStdio(
                server("{\"url\":\"https://h/mcp\",\"command\":\"  \"}")));
        Assertions.assertFalse(McpClientManagerServiceImpl.isStdio(null));
    }
}
