package com.mio.ai.resource.service.mcp.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MCP 失败原因可读性回归测试：notice 会把这段文案直接展示给用户，
 * SDK 原文（"Client failed to initialize by explicit API call" 之类）对用户无指导意义。
 */
class McpFailureReasonTest {

    @Test
    void sdkInitializeMessageBecomesReadableChinese() {
        String reason = McpClientManagerServiceImpl.shortReason(
                new IllegalStateException("Client failed to initialize by explicit API call"));
        Assertions.assertEquals("握手失败，对方可能不是可用的 MCP 服务", reason);
        Assertions.assertFalse(reason.contains("Client failed"), "不应透出 SDK 原文");
    }

    @Test
    void connectionFailuresAreClassified() {
        Assertions.assertEquals("连接超时，服务未响应",
                McpClientManagerServiceImpl.shortReason(new RuntimeException("HTTP connect timed out")));
        Assertions.assertEquals("连接被拒绝，服务可能未启动",
                McpClientManagerServiceImpl.shortReason(
                        new RuntimeException("java.net.ConnectException: Connection refused")));
        Assertions.assertEquals("域名解析失败，请检查地址",
                McpClientManagerServiceImpl.shortReason(
                        new RuntimeException("java.net.UnknownHostException: bad.host")));
    }

    @Test
    void authFailuresAreClassified() {
        Assertions.assertEquals("认证失败，请检查访问令牌",
                McpClientManagerServiceImpl.shortReason(new RuntimeException("HTTP 401 Unauthorized")));
        Assertions.assertEquals("无权访问该服务",
                McpClientManagerServiceImpl.shortReason(new RuntimeException("HTTP 403 Forbidden")));
    }

    @Test
    void unknownErrorsAreTruncatedNotDumped() {
        String longMsg = "Some very unusual upstream failure ".repeat(10);
        String reason = McpClientManagerServiceImpl.shortReason(new RuntimeException(longMsg));
        Assertions.assertTrue(reason.length() <= 84, "未知错误必须截断: " + reason.length());
    }

    @Test
    void blankMessageFallsBackToExceptionType() {
        String reason = McpClientManagerServiceImpl.shortReason(new NullPointerException());
        Assertions.assertTrue(reason.startsWith("初始化失败"), reason);
    }
}
