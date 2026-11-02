package com.mio.ai.common.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 客户端 IP 解析回归测试：核心是「客户端自填转发头不能冒充他人」——
 * 游客的配额记账与会话鉴权都以该 IP 为归属键，伪造即等于盗用他人身份。
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void spoofedForwardedHeaderFromPublicPeerIsIgnored() {
        // 直连对端是公网地址（非自家代理）：转发头全部不可信
        MockHttpServletRequest req = request("203.0.113.9");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        req.addHeader("X-Real-IP", "5.6.7.8");
        Assertions.assertEquals("203.0.113.9", ClientIpResolver.resolve(req));
    }

    @Test
    void realIpIsPreferredBehindTrustedProxy() {
        // 自家 nginx：对端为内网，X-Real-IP 由 nginx 用 $remote_addr 覆盖写，可信
        MockHttpServletRequest req = request("172.18.0.2");
        req.addHeader("X-Real-IP", "198.51.100.7");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7");
        Assertions.assertEquals("198.51.100.7", ClientIpResolver.resolve(req));
    }

    @Test
    void forwardedForFallsBackToRightmostHopNotClientSuppliedFirst() {
        // 无 X-Real-IP 时取最右一跳（最近代理观察到的地址），而非客户端自填的首段
        MockHttpServletRequest req = request("127.0.0.1");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.7");
        Assertions.assertEquals("198.51.100.7", ClientIpResolver.resolve(req));
    }

    @Test
    void malformedForwardedValuesAreRejected() {
        MockHttpServletRequest req = request("10.0.0.5");
        req.addHeader("X-Real-IP", "not-an-ip; DROP TABLE");
        Assertions.assertEquals("10.0.0.5", ClientIpResolver.resolve(req));

        MockHttpServletRequest blank = request("10.0.0.6");
        blank.addHeader("X-Real-IP", "unknown");
        blank.addHeader("X-Forwarded-For", "   ");
        Assertions.assertEquals("10.0.0.6", ClientIpResolver.resolve(blank));
    }

    @Test
    void ipv6LoopbackAndSiteLocalAreTreatedAsTrustedProxies() {
        Assertions.assertTrue(ClientIpResolver.isTrustedProxy("::1"));
        Assertions.assertTrue(ClientIpResolver.isTrustedProxy("10.1.2.3"));
        Assertions.assertTrue(ClientIpResolver.isTrustedProxy("192.168.0.10"));
        Assertions.assertFalse(ClientIpResolver.isTrustedProxy("203.0.113.9"));
        Assertions.assertFalse(ClientIpResolver.isTrustedProxy(null));
    }

    @Test
    void nullRequestIsSafe() {
        Assertions.assertEquals("unknown", ClientIpResolver.resolve(null));
    }
}
