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

    /**
     * 内置元数据兜底（取自 opencode models.dev 目录）：zen go 官方 /models 只回 id，
     * 上下文窗口/视觉/推理标记无从解析——已知免费模型按此表补齐，表外模型保持 0/false
     * （前端对缺省窗口回退配置值，档位按推理模型处理）。
     */
    private static final Map<String, FreeModel> BUILTIN_META = Map.of(
            "cat-jam-free", new FreeModel("cat-jam-free", false, 262_144, true),
            "longcat-2.5-preview-free", new FreeModel("longcat-2.5-preview-free", true, 1_000_000, true),
            "space-bunny-free", new FreeModel("space-bunny-free", true, 1_048_576, true),
            "step-5-preview-free", new FreeModel("step-5-preview-free", true, 1_000_000, true),
            "mimo-v2.6-flash-free", new FreeModel("mimo-v2.6-flash-free", true, 200_000, true),
            "muse-spark-1.2-contributor-free", new FreeModel("muse-spark-1.2-contributor-free", true, 1_048_576, true),
            "ling-3.1-flash-free", new FreeModel("ling-3.1-flash-free", false, 262_144, true),
            "ling-3.0-flash-fin-free", new FreeModel("ling-3.0-flash-fin-free", false, 262_144, true));

    /**
     * 免费模型元数据：id + 视觉输入 + 上下文窗口（0=元数据缺失）+ 是否推理模型。
     * 上下文窗口与推理标记来自反代 /models 的元数据（context_window / reasoning），
     * 供聊天页思考档位与上下文容量指示按所选模型如实渲染。
     */
    public record FreeModel(String id, boolean vision, long contextWindow, boolean reasoning) {
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

    /** 按 id 查目录（未收录回 null） */
    public FreeModel find(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        for (FreeModel model : listModels()) {
            if (model.id().equals(modelId)) {
                return model;
            }
        }
        return null;
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
                    FreeModel parsed = new FreeModel(id, hasImageInput(map), contextWindowOf(map), reasoningOf(map));
                    FreeModel builtin = BUILTIN_META.get(id);
                    // 上游缺元数据（zen go 官方端点只回 id）时按内置表补齐
                    if (parsed.contextWindow() <= 0 && !parsed.vision() && !parsed.reasoning()
                            && builtin != null) {
                        parsed = builtin;
                    }
                    ids.add(parsed);
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

    /** 上下文窗口（token）：context_window / context_length / metadata.context_window，缺省 0 */
    private long contextWindowOf(Map<?, ?> model) {
        long window = longOf(model.get("context_window"));
        if (window <= 0) {
            window = longOf(model.get("context_length"));
        }
        if (window <= 0 && model.get("metadata") instanceof Map<?, ?> meta) {
            window = longOf(meta.get("context_window"));
        }
        return Math.max(0, window);
    }

    /** 是否推理模型：reasoning / supports_reasoning / metadata.reasoning，缺省 false */
    private boolean reasoningOf(Map<?, ?> model) {
        if (truthy(model.get("reasoning")) || truthy(model.get("supports_reasoning"))) {
            return true;
        }
        return model.get("metadata") instanceof Map<?, ?> meta && truthy(meta.get("reasoning"));
    }

    private long longOf(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
