package com.mio.ai.bot.model.dto;

import com.mio.ai.common.utils.JacksonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话附件（沙箱工作区内相对路径）：
 * path 形如 uploads/&lt;chatId&gt;/&lt;file&gt;（用户上传）或 outputs/&lt;chatId&gt;/&lt;file&gt;（Agent 产出）。
 */
public record AttachmentItem(String path, String name, long size) {

    /** 暂存根白名单：下载/引用只放行这两个目录，防路径穿越与任意文件读取 */
    private static final List<String> ALLOWED_ROOTS = List.of("uploads/", "outputs/");

    /** 路径是否合法：暂存根内、无穿越、无可疑字符 */
    public static boolean isStagedPath(String path) {
        if (path == null || path.isBlank() || path.length() > 512) {
            return false;
        }
        String p = path.trim();
        if (p.startsWith("/") || p.contains("\\") || p.contains("..")) {
            return false;
        }
        return ALLOWED_ROOTS.stream().anyMatch(p::startsWith);
    }

    /**
     * 暂存路径 uploads|outputs/&lt;chatId&gt;/&lt;file&gt; 中的 chatId（归属校验用）。
     * 形态不符（段数不足或为空的 chatId 段）返回 null，调用方按无法判定处理。
     */
    public static String chatIdOfStagedPath(String path) {
        if (!isStagedPath(path)) {
            return null;
        }
        String[] segments = path.trim().split("/");
        return segments.length >= 3 && !segments[1].isBlank() ? segments[1] : null;
    }

    /** 从前端 JSON 数组解析附件引用，非法项直接丢弃（宁可少一个附件不让坏路径进上下文） */
    public static List<AttachmentItem> parseList(String json) {
        List<AttachmentItem> items = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return items;
        }
        try {
            List<?> raw = JacksonUtil.readValue(json, List.class);
            if (raw == null) {
                return items;
            }
            for (Object o : raw) {
                if (!(o instanceof Map<?, ?> map)) {
                    continue;
                }
                String path = map.get("path") == null ? null : String.valueOf(map.get("path")).trim();
                if (!isStagedPath(path)) {
                    continue;
                }
                String name = map.get("name") == null ? path.substring(path.lastIndexOf('/') + 1)
                        : String.valueOf(map.get("name"));
                long size = 0;
                if (map.get("size") instanceof Number n) {
                    size = n.longValue();
                }
                items.add(new AttachmentItem(path, name, size));
                if (items.size() >= 50) {
                    break;
                }
            }
        } catch (Exception ignored) {
            // 解析失败按无附件处理
        }
        return items;
    }

    /** 展示块/SSE 负载形态 */
    public Map<String, Object> toMap() {
        return new java.util.LinkedHashMap<>(java.util.Map.of(
                "path", path, "name", name == null ? "" : name, "size", size));
    }
}
