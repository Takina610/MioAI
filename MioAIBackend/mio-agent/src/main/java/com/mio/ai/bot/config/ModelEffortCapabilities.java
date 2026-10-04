package com.mio.ai.bot.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 当前模型的思考档位能力探测：上游对各 reasoning_effort 的支持情况并不统一
 * （如 fledge-alpha-free 仅支持 low/high，minimal/medium/none 会 400），且
 * 模型元数据不含该信息——这里用 1-token 探测请求实测并缓存，供前端如实渲染档位。
 */
@Slf4j
@Component
public class ModelEffortCapabilities {

    /** 全量候选档位（与 ReasoningEffort 枚举一致，按强度排序） */
    private static final List<String> ALL_EFFORTS = List.of("minimal", "low", "medium", "high", "xhigh", "max", "none");

    private static final long CACHE_TTL_MS = 3600_000L;

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    private volatile List<String> cached;
    private volatile long cachedAt;
    private final Map<String, Object> probeLocks = new ConcurrentHashMap<>();

    public ModelEffortCapabilities(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                   @Value("${spring.ai.openai.api-key}") String apiKey,
                                   @Value("${spring.ai.openai.chat.model}") String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    /** 支持的档位列表（探测失败回退全量，前端宁可多显示也不能空） */
    public List<String> supportedEfforts() {
        List<String> snapshot = cached;
        if (snapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (probeLocks.computeIfAbsent("probe", k -> new Object())) {
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cached;
            }
            List<String> supported = ALL_EFFORTS.parallelStream()
                    .filter(this::probe)
                    .toList();
            if (supported.isEmpty()) {
                // 整体探测失败（网络/反代不可达）：不缓存，回退全量
                log.warn("思考档位探测整体失败，回退全量档位");
                return ALL_EFFORTS;
            }
            cached = supported;
            cachedAt = System.currentTimeMillis();
            log.info("模型 {} 支持的思考档位: {}", model, supported);
            return supported;
        }
    }

    /**
     * 单档探测：200 = 支持；400 且错误信息点名 reasoning_effort = 不支持；
     * 其他结果（限流/超时等）保守视为支持，避免误砍可用档位。
     */
    private boolean probe(String effort) {
        String body = JacksonUtil.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "1")),
                "max_tokens", 1,
                "reasoning_effort", effort));
        try (HttpResponse resp = HttpRequest.post(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .timeout(8000)
                .execute()) {
            if (resp.getStatus() == 200) {
                return true;
            }
            if (resp.getStatus() == 400 && resp.body().contains("reasoning_effort")) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("档位 {} 探测异常，保守视为支持: {}", effort, e.getMessage());
            return true;
        }
    }
}
