package com.mio.ai.bot.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenCode 免费模型目录：经本地 opencode2api 反代拉取 OpenAI 兼容 /models 列表，
 * 供个人中心模型设置与聊天页模型切换器展示。
 * <p>鉴权用反代的本地 server key（spring.ai.openai.api-key）。已实测：OPENCODE_ZEN_KEY
 * 是 opencode zen go 网关的 x-api-key（非本反代的本地 key，也非 Bearer），且 go 池 chat
 * 还需 OpenCode 客户端 OAuth credential——拿到可用凭证前免费档统一走反代。
 * 仅保留 -free 结尾的免费模型；短 TTL 内存缓存，反代不可达时返回空列表。
 */
@Slf4j
@Component
public class OpenCodeModelCatalog {

    private static final long CACHE_TTL_MS = 300_000L;
    private static final int HTTP_TIMEOUT_MS = 10_000;

    public record FreeModel(String id, boolean vision) {
    }

    private final String baseUrl;
    private final String apiKey;

    private volatile List<FreeModel> cachedModels = List.of();
    private volatile long cachedAt = 0;

    public OpenCodeModelCatalog(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /** 免费模型目录（-free 结尾，字典序）；拉取失败回退上次成功结果，再退空列表 */
    public List<FreeModel> listModels() {
        long now = System.currentTimeMillis();
        List<FreeModel> cached = cachedModels;
        if (now - cachedAt < CACHE_TTL_MS && !cached.isEmpty()) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedModels;
            if (now - cachedAt < CACHE_TTL_MS && !cached.isEmpty()) {
                return cached;
            }
            List<FreeModel> models = fetch();
            if (!models.isEmpty()) {
                cachedModels = models;
                cachedAt = now;
                return models;
            }
            return cached;
        }
    }

    @SuppressWarnings("unchecked")
    private List<FreeModel> fetch() {
        try (HttpResponse resp = HttpRequest.get(baseUrl + "/models")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(HTTP_TIMEOUT_MS)
                .execute()) {
            if (resp.getStatus() != 200) {
                log.warn("免费模型列表拉取失败: HTTP {} / {}", resp.getStatus(),
                        cn.hutool.core.util.StrUtil.brief(resp.body(), 160));
                return List.of();
            }
            Object body = JacksonUtil.readValue(resp.body(), Object.class);
            List<?> data = null;
            if (body instanceof Map<?, ?> map && map.get("data") instanceof List<?> list) {
                data = list;
            } else if (body instanceof List<?> list) {
                data = list;
            }
            List<FreeModel> ids = new ArrayList<>();
            if (data != null) {
                for (Object item : data) {
                    if (!(item instanceof Map<?, ?> map) || map.get("id") == null) {
                        continue;
                    }
                    String id = String.valueOf(map.get("id"));
                    if (!id.endsWith("-free")) {
                        continue;
                    }
                    ids.add(new FreeModel(id, hasImageInput(map)));
                }
            }
            ids.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.id(), b.id()));
            return List.copyOf(ids);
        } catch (Exception e) {
            log.warn("免费模型列表拉取异常: {}", e.getMessage());
            return List.of();
        }
    }

    /** input_modalities 含 image 即视为视觉模型（元数据缺失时回退 id 含 vl/vision 的粗判） */
    private boolean hasImageInput(Map<?, ?> model) {
        Object modalities = model.get("input_modalities");
        if (modalities instanceof List<?> list) {
            return list.stream().anyMatch(m -> "image".equalsIgnoreCase(String.valueOf(m)));
        }
        String id = String.valueOf(model.get("id")).toLowerCase();
        return id.contains("-vl") || id.contains("vision");
    }
}
