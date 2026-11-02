package com.mio.ai.common.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 出站地址策略纯单元测试：只校验协议与字面 IP，不做 DNS 解析依赖外部网络
 */
class OutboundUrlGuardTest {

    @Test
    void rejectsNonHttpSchemes() {
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("file:///etc/passwd"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("ftp://example.com/x"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("gopher://example.com"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("not a url"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl(null));
    }

    @Test
    void rejectsLoopbackAndPrivateLiterals() {
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://127.0.0.1:8090/v1"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://localhost/v1"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://10.0.0.8/v1"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://192.168.1.1/v1"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://172.16.5.4/v1"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://169.254.169.254/latest/meta-data"));
        Assertions.assertFalse(OutboundUrlGuard.isPublicHttpUrl("http://0.0.0.0/v1"));
    }

    @Test
    void acceptsPublicLiteralAddress() {
        // 8.8.8.8 为公网字面地址，无需 DNS
        Assertions.assertTrue(OutboundUrlGuard.isPublicHttpUrl("https://8.8.8.8/v1"));
        Assertions.assertTrue(OutboundUrlGuard.isPublicHttpUrl("http://1.1.1.1/"));
    }
}
