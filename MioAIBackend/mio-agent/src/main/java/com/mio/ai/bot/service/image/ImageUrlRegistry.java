package com.mio.ai.bot.service.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.common.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片资产登记簿（防伪造 + 元数据回填）：image_search 工具返回的每个候选按会话登记
 * （完整 ImageResult JSON），render_ui 图片块校验时按 url 核对——模型编造/篡改的 URL
 * 进不了渲染管线；命中的 url 自动回填模型未携带的来源/作者/授权元数据（验收第 7 条
 * 的确定性来源，不依赖弱模型抄写）。Redis List 存储，TTL 7 天，超上限清空重建。
 */
@Component
public class ImageUrlRegistry {

    private static final String KEY_PREFIX = "mio:bot:img:allow:";
    private static final long TTL_SECONDS = 7 * 24 * 3600L;
    private static final int MAX_ENTRIES = 600;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource(name = "redisUtil")
    private RedisUtil redisUtil;

    /** 工具返回后登记完整候选（url 即键，重复登记原地去重） */
    public void register(String chatId, List<ImageResult> results) {
        if (chatId == null || results == null || results.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + chatId;
        Map<String, String> byUrl = new LinkedHashMap<>();
        for (String json : redisUtil.getList(key)) {
            ImageResult result = parse(json);
            if (result != null && result.imageUrl() != null) {
                byUrl.put(result.imageUrl(), json);
            }
        }
        for (ImageResult result : results) {
            if (result.imageUrl() == null) {
                continue;
            }
            byUrl.put(result.imageUrl(), write(result));
            if (result.thumbnailUrl() != null && !result.thumbnailUrl().equals(result.imageUrl())) {
                byUrl.put(result.thumbnailUrl(), write(result));
            }
        }
        if (byUrl.size() > MAX_ENTRIES) {
            byUrl.keySet().stream().skip(byUrl.size() - MAX_ENTRIES)
                    .forEach(byUrl::remove);
        }
        redisUtil.lPushAll(key, new ArrayList<>(byUrl.values()), TTL_SECONDS);
    }

    /** render_ui 校验：该 url 是否为本会话 image_search 真实结果；命中返回完整元数据 */
    public ImageResult lookup(String chatId, String url) {
        if (chatId == null || url == null) {
            return null;
        }
        for (String json : redisUtil.getList(KEY_PREFIX + chatId)) {
            ImageResult result = parse(json);
            if (result != null && (url.equals(result.imageUrl()) || url.equals(result.thumbnailUrl()))) {
                return result;
            }
        }
        return null;
    }

    private static String write(ImageResult result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static ImageResult parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ImageResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}
