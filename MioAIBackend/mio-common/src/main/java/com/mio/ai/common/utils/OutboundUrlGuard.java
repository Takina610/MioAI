package com.mio.ai.common.utils;

import java.net.InetAddress;
import java.net.URL;

/**
 * 出站地址策略：后端代发的用户可控 URL 只允许公网 http(s)。
 * <p>拦截回环/私网/链路本地/组播/任意本地地址与 IPv6 唯一本地地址，
 * 阻断把内网服务（模型反代、元数据端点、内网管理台）暴露给任意输入的 SSRF 面。
 * <p>注意：本工具只做「解析结果」判定，DNS 重绑定仍可能绕过——需要更强保证时
 * 应在连接层固定已校验的 IP。
 */
public final class OutboundUrlGuard {

    private OutboundUrlGuard() {
    }

    /** URL 是否为可安全抓取的公网 http(s) 地址 */
    public static boolean isPublicHttpUrl(String url) {
        try {
            URL parsed = new URL(url);
            return isPublicHttpUrl(parsed);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPublicHttpUrl(URL parsed) {
        try {
            String protocol = parsed.getProtocol();
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                return false;
            }
            String host = parsed.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 单个解析地址是否为公网地址 */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        // IPv6 unique local fc00::/7
        byte[] raw = address.getAddress();
        return raw.length != 16 || (raw[0] & 0xFE) != 0xFC;
    }
}
