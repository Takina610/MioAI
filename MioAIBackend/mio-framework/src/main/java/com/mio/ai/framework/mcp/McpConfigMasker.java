package com.mio.ai.framework.mcp;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.List;

/**
 * MCP 配置脱敏：对外展示（广场/详情/智能体详情）时隐藏凭证，保留结构以便用户理解配置形态。
 * <p>隐藏范围：headers 与 env 的全部值、url 中的 query 参数值、args 中形如
 * *TOKEN*&#47;*KEY*&#47;*SECRET* 的 "KEY=VALUE" 值。命令名、参数形态、主机地址保留——
 * 它们是配置说明，不构成凭证。
 */
public final class McpConfigMasker {

    private static final String MASK = "***";

    private McpConfigMasker() {
    }

    /**
     * 配置是否已被脱敏：用于挡住「把掩码原样保存回库」。
     * <p>判定口径与 {@link #mask} 一致——headers/env 的值、url query 值、
     * args 里的敏感 "K=V" 值中，只要存在恰好等于掩码串的值即认定为脱敏态。
     * 真实配置里几乎不会出现整值等于 "***" 的字段，误判风险极低。
     */
    public static boolean isMasked(String config) {
        if (config == null || config.isBlank()) {
            return false;
        }
        try {
            JSONObject root = JSONUtil.parseObj(config);
            JSONObject servers = root.getJSONObject("mcpServers");
            if (servers == null || servers.isEmpty()) {
                return false;
            }
            for (String serverName : servers.keySet()) {
                JSONObject server = servers.getJSONObject(serverName);
                if (server == null) {
                    continue;
                }
                if (containsMaskedValue(server.getJSONObject("headers"))
                        || containsMaskedValue(server.getJSONObject("env"))
                        || (server.getStr("url") != null && server.getStr("url").contains("=" + MASK))
                        || containsMaskedArg(server.getJSONArray("args"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean containsMaskedValue(JSONObject map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (String key : map.keySet()) {
            if (MASK.equals(String.valueOf(map.get(key)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMaskedArg(JSONArray args) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        for (int i = 0; i < args.size(); i++) {
            String text = String.valueOf(args.get(i));
            if (text.endsWith("=" + MASK)) {
                return true;
            }
        }
        return false;
    }

    /** 脱敏 mcpServers 下全部服务节点；解析失败原样返回（畸形配置不展示细节） */
    public static String mask(String config) {
        if (config == null || config.isBlank()) {
            return config;
        }
        try {
            JSONObject root = JSONUtil.parseObj(config);
            JSONObject servers = root.getJSONObject("mcpServers");
            if (servers == null || servers.isEmpty()) {
                return config;
            }
            for (String serverName : servers.keySet()) {
                JSONObject server = servers.getJSONObject(serverName);
                if (server == null) {
                    continue;
                }
                maskMap(server.getJSONObject("headers"));
                maskMap(server.getJSONObject("env"));
                if (server.getStr("url") != null) {
                    server.set("url", maskUrl(server.getStr("url")));
                }
                maskArgs(server.getJSONArray("args"));
            }
            return root.toString();
        } catch (Exception e) {
            return config;
        }
    }

    private static void maskMap(JSONObject map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        for (String key : map.keySet()) {
            map.set(key, MASK);
        }
    }

    /** url 的 query 值一律脱敏（token/key 常挂在 query 上）；其余部分保留 */
    private static String maskUrl(String url) {
        int query = url.indexOf('?');
        if (query < 0 || query == url.length() - 1) {
            return url;
        }
        String[] pairs = url.substring(query + 1).split("&");
        StringBuilder masked = new StringBuilder(url.substring(0, query + 1));
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                masked.append('&');
            }
            int eq = pairs[i].indexOf('=');
            masked.append(eq < 0 ? pairs[i] : pairs[i].substring(0, eq + 1) + MASK);
        }
        return masked.toString();
    }

    /** args 里的 "K=V" 形式：键名带 token/key/secret/password 时脱敏值 */
    private static void maskArgs(JSONArray args) {
        if (args == null || args.isEmpty()) {
            return;
        }
        List<String> sensitiveKeys = List.of("token", "key", "secret", "password", "passwd", "credential");
        for (int i = 0; i < args.size(); i++) {
            Object item = args.get(i);
            if (!(item instanceof String text)) {
                continue;
            }
            int eq = text.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = text.substring(0, eq).toLowerCase();
            if (sensitiveKeys.stream().anyMatch(key::contains)) {
                args.set(i, text.substring(0, eq + 1) + MASK);
            }
        }
    }
}
