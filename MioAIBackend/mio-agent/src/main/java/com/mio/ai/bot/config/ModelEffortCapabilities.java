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

    /** 探测不可判定（网关不可达/整体限流）时的兜底档位：宁可少给也不能给出会 400 的档位 */
    private static final List<String> DEFAULT_EFFORTS = List.of("low", "medium", "high");

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

    /** 支持的档位列表（探测失败回退默认子集，前端对空列表还有自己的兜底） */
    public List<String> supportedEfforts() {
        List<String> snapshot = cached;
        if (snapshot != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (probeLocks.computeIfAbsent("probe", k -> new Object())) {
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cached;
            }
            List<ProbeResult> results = ALL_EFFORTS.parallelStream()
                    .map(this::probe)
                    .toList();
            // 一个可判定的答案都没有 = 网关不可达/整体异常。此时全量兜底会让用户选到必然 400
            // 的档位（这正是"档位突然变多"事故的根因），改为返回默认子集且不缓存，网关恢复后重新探测
            if (results.stream().noneMatch(ProbeResult::definitive)) {
                log.warn("思考档位探测不可判定（网关 {} 无有效响应），返回默认档位且不缓存", baseUrl);
                return DEFAULT_EFFORTS;
            }
            List<String> supported = results.stream()
                    .filter(ProbeResult::supported)
                    .map(ProbeResult::effort)
                    .toList();
            if (supported.isEmpty()) {
                // 模型对 reasoning_effort 全部明确拒绝：如实返回空，前端会隐藏/兜底档位选择
                log.info("模型 {} 明确不支持任何思考档位", model);
                cached = List.of();
                cachedAt = System.currentTimeMillis();
                return cached;
            }
            cached = supported;
            cachedAt = System.currentTimeMillis();
            log.info("模型 {} 支持的思考档位: {}", model, supported);
            return supported;
        }
    }

    /**
     * 单档探测结果：supported=该档位是否可用；definitive=网关是否给出了可判定的答案
     * （200 或点名 reasoning_effort 的 400）。限流/超时等模糊结果按支持处理但不算可判定。
     */
    private record ProbeResult(String effort, boolean supported, boolean definitive) {
    }

    private ProbeResult probe(String effort) {
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
                return new ProbeResult(effort, true, true);
            }
            if (resp.getStatus() == 400 && resp.body().contains("reasoning_effort")) {
                return new ProbeResult(effort, false, true);
            }
            return new ProbeResult(effort, true, false);
        } catch (Exception e) {
            log.debug("档位 {} 探测异常: {}", effort, e.getMessage());
            return new ProbeResult(effort, true, false);
        }
    }
}
