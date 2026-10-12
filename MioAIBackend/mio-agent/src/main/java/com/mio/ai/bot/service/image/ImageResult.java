package com.mio.ai.bot.service.image;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一图片数据结构（图文混排协议的图片资源单元）：
 * 字段缺失一律为空，不编造来源/作者/授权——各 Provider 照实透传，
 * 模型只负责选择与组织，不负责伪造真实性。
 */
public record ImageResult(
        String id,
        String imageUrl,
        String thumbnailUrl,
        String sourceUrl,
        String sourceName,
        String title,
        String desc,
        String author,
        String license,
        Integer width,
        Integer height) {

    /** 剔除空字段（工具回执与前端载荷共用形态，无 null 噪声） */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "id", id);
        put(map, "imageUrl", imageUrl);
        put(map, "thumbnailUrl", thumbnailUrl);
        put(map, "sourceUrl", sourceUrl);
        put(map, "sourceName", sourceName);
        put(map, "title", title);
        put(map, "desc", desc);
        put(map, "author", author);
        put(map, "license", license);
        put(map, "width", width);
        put(map, "height", height);
        return map;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            map.put(key, value);
        }
    }
}
