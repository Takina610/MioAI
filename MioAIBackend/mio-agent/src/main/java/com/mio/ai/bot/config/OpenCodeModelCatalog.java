package com.mio.ai.bot.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.common.utils.RedisUtil;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OpenCode 免费模型目录：经本地 opencode2api 反代拉取 OpenAI 兼容 /models 列表，
 * 供个人中心模型设置与聊天页模型切换器展示。
 * <p>鉴权用反代的本地 server key（spring.ai.openai.api-key）。已实测：OPENCODE_ZEN_KEY
 * 是 opencode zen go 网关的 x-api-key（非本反代的本地 key，也非 Bearer），且 go 池 chat
 * 还需 OpenCode 客户端 OAuth credential——拿到可用凭证前免费档统一走反代。
 * 仅保留 -free 结尾的免费模型。
 * <p>目录缓存落在 Redis（TTL 一天）：进程重启与多实例共享同一份；
 * 每次接口调用都读 Redis，未命中/过期才回源拉取并写回。
 * 免费模型出现服务端故障时由上层调用 {@link #invalidate()} 删除缓存并异步重取覆盖。
 */
@Slf4j
@Component
public class OpenCodeModelCatalog {

    /** Redis 键：免费模型目录（JSON 数组） */
    private static final String REDIS_KEY = "mio:model:free-catalog";

    /** 目录缓存有效期（秒）：一天 */
    private static final long CACHE_TTL_SECONDS = 24 * 3600L;

    private static final int HTTP_TIMEOUT_MS = 10_000;

    /** 回源失败后的冷却期：上游不可用时不至于每次请求都打一次上游（不影响读缓存命中路径） */
    private static final long FETCH_FAILURE_COOLDOWN_MS = 30_000L;

    /** 失效重建去重窗口：同一轮的主步+兜底收束会各上报一次，窗口内只重建一次 */
    private static final long INVALIDATE_DEBOUNCE_MS = 15_000L;

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

    @Resource(name = "redisUtil")
    private RedisUtil redisUtil;

    private final String baseUrl;
    private final String apiKey;

    /** 上次回源失败时刻（0=上次成功）：冷却期内不再回源 */
    private volatile long lastFetchFailureAt = 0;

    /** 上次失效重建时刻：invalidate 去重窗口的锚点 */
    private volatile long lastInvalidateAt = 0;

    /** 失效重建去重锁（仅保护 lastInvalidateAt 的读改写） */
    private final Object invalidateGate = new Object();

    /** 异步重取执行器：失效重建不阻塞正在收尾的对话轮次（随应用退出关闭） */
    private final ExecutorService refresher = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "free-model-catalog-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public OpenCodeModelCatalog(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @PreDestroy
    void shutdown() {
        refresher.shutdownNow();
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

    /**
     * 免费模型目录（-free 结尾，字典序）。
     * 每次调用先读 Redis：命中直接返回；未命中/过期/内容损坏才回源拉取并写回（TTL 一天）。
     * 回源失败返回空列表（前端展示为空，下次请求再试；冷却期内不重复回源）。
     */
    public List<FreeModel> listModels() {
        List<FreeModel> cached = readCache();
        return cached.isEmpty() ? refresh() : cached;
    }

    /**
     * 失效重建：删除 Redis 缓存并异步回源重取覆盖。
     * 免费模型出现服务端故障（非用户取消、非上下文超限）时调用——
     * 该模型可能已被上游下线/改名，旧目录不再可信。
     * <p>同一轮故障会从主步与兜底收束各上报一次，此处按时间窗口去重，
     * 避免同一故障触发多次回源。
     */
    public void invalidate() {
        long now = System.currentTimeMillis();
        synchronized (invalidateGate) {
            if (now - lastInvalidateAt < INVALIDATE_DEBOUNCE_MS) {
                return;
            }
            lastInvalidateAt = now;
        }
        redisUtil.delete(REDIS_KEY);
        log.info("免费模型目录已失效，准备异步重取");
        try {
            refresher.execute(() -> {
                List<FreeModel> models = refresh();
                log.info("免费模型目录重建完成，共 {} 个", models.size());
            });
        } catch (Exception e) {
            // 执行器已关闭（应用退出中）：下次请求读缓存未命中时会自然回源
            log.warn("免费模型目录异步重取提交失败: {}", e.getMessage());
        }
    }

    /** 回源拉取并写入 Redis（listModels 未命中与 invalidate 重取共用） */
    public List<FreeModel> refresh() {
        synchronized (this) {
            if (System.currentTimeMillis() - lastFetchFailureAt < FETCH_FAILURE_COOLDOWN_MS) {
                return List.of();
            }
            List<FreeModel> models = fetch();
            if (models.isEmpty()) {
                lastFetchFailureAt = System.currentTimeMillis();
                return List.of();
            }
            lastFetchFailureAt = 0;
            writeCache(models);
            return models;
        }
    }

    /** 读 Redis 缓存；内容无法解析时删键并按未命中处理（下次调用回源重建） */
    private List<FreeModel> readCache() {
        String json;
        try {
            json = redisUtil.get(REDIS_KEY);
        } catch (Exception e) {
            log.warn("免费模型目录缓存读取失败: {}", e.getMessage());
            return List.of();
        }
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<FreeModel> models = JacksonUtil.readListValue(json, FreeModel.class);
            return models == null ? List.of() : List.copyOf(models);
        } catch (Exception e) {
            log.warn("免费模型目录缓存解析失败，按未命中处理: {}", e.getMessage());
            redisUtil.delete(REDIS_KEY);
            return List.of();
        }
    }

    private void writeCache(List<FreeModel> models) {
        try {
            redisUtil.set(REDIS_KEY, JacksonUtil.writeValueAsString(models), CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("免费模型目录缓存写入失败: {}", e.getMessage());
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
    /**
     * input_modalities 含 image 即视为视觉模型；顶层缺失时再看 metadata（opencode2api
     * 把模态放在 metadata.input_modalities）；都无则回退 id 含 vl/vision 的粗判。
     */
    private boolean hasImageInput(Map<?, ?> model) {
        Boolean fromList = imageIn(listOf(model.get("input_modalities")));
        if (fromList != null) {
            return fromList;
        }
        if (model.get("metadata") instanceof Map<?, ?> meta) {
            Boolean nested = imageIn(listOf(meta.get("input_modalities")));
            if (nested != null) {
                return nested;
            }
        }
        String id = String.valueOf(model.get("id")).toLowerCase();
        return id.contains("-vl") || id.contains("vision");
    }

    /** 模态判定：列表非空时按是否含 image 判定；缺失/空列表回 null（交由下一级兜底） */
    private Boolean imageIn(List<?> modalities) {
        if (modalities == null || modalities.isEmpty()) {
            return null;
        }
        return modalities.stream().anyMatch(m -> "image".equalsIgnoreCase(String.valueOf(m)));
    }

    private List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : null;
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
