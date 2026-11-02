package com.mio.ai.common.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析（游客归属判定的基础）。
 * <p>安全模型：只在「直连对端是受信代理」时才采信转发头，否则一律用对端地址——
 * 否则客户端自填头即可冒充他人（游客按 IP 记账，冒充即等于盗用配额/越权操作会话）。
 * <p>受信代理判定：对端为回环或内网地址（即本项目自建的 nginx 反代，后端本身不对外暴露端口）。
 * <p>转发头顺序：X-Real-IP 优先。本仓库的 nginx 配置用
 * {@code proxy_set_header X-Real-IP $remote_addr} 覆盖写（客户端无法伪造），
 * 而 X-Forwarded-For 是 {@code $proxy_add_x_forwarded_for} 追加语义——
 * 首段就是客户端自己发来的原值，不可信，仅作为兜底且只取最右侧一跳。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /** 解析客户端 IP；永远返回非空字符串 */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = request.getRemoteAddr();
        if (!isTrustedProxy(remote)) {
            // 对端不是自家代理：转发头全部不可信
            return blankToUnknown(remote);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (isValidIp(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 追加语义下最右侧一跳是最近代理观察到的地址，比首段可信
            String[] hops = forwarded.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (isValidIp(hop)) {
                    return hop;
                }
            }
        }
        return blankToUnknown(remote);
    }

    /** 对端是否为受信代理（本机或内网：部署中 nginx 与服务同机/同 docker 网络） */
    public static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isAnyLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /** 转发头值是否为可用的单值 IP（拒绝空串/unknown/逗号串等非法形态） */
    static boolean isValidIp(String value) {
        if (value == null || value.isBlank() || "unknown".equalsIgnoreCase(value.trim())) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.contains(",") || candidate.length() > 64) {
            return false;
        }
        // 仅允许 IP 字面量形态，防止把任意字符串用作归属键
        return candidate.matches("[0-9A-Fa-f:.\\[\\]]+");
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
