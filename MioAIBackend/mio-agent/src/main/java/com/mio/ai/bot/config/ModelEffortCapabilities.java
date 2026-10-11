package com.mio.ai.bot.config;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.core.util.StrUtil;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按模型探测思考档位能力：上游对各 reasoning_effort 的支持情况并不统一
 * （如 fledge-alpha-free 仅支持 low/high，step-5-preview 的 none 会 400），且
 * 模型元数据不含该信息——这里用 1-token 探测请求实测并按模型缓存，供前端如实渲染档位。
 */
@Slf4j
@Component
public class ModelEffortCapabilities {

    /** 全量候选档位（与 ReasoningEffort 枚举一致，按强度排序） */
    private static final List<String> ALL_EFFORTS = List.of("minimal", "low", "medium", "high", "xhigh", "max", "none");

    /** 探测不可判定（网关不可达/整体限流）时的兜底档位：宁可少给也不能给出会 400 的档位 */
    private static final List<String> DEFAULT_EFFORTS = List.of("low", "medium", "high");

    private static final long CACHE_TTL_MS = 3600_000L;

    /** 单档探测超时：反代串行吞吐下慢档位（minimal）可能排队，给足余量又不过分拖住冷启动 */
    private static final int PROBE_TIMEOUT_MS = 12_000;

    private final String baseUrl;
    private final String apiKey;

    /** 探测结果按模型分键缓存：换模型（重启）即重新探测，互不串档 */
    private final Map<String, CachedCapabilities> cacheByModel = new ConcurrentHashMap<>();
    private final Map<String, Object> probeLocks = new ConcurrentHashMap<>();

    public ModelEffortCapabilities(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                   @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /** 指定模型支持的档位列表（探测失败回退默认子集，前端对空列表还有自己的兜底） */
    public List<String> supportedEfforts(String model) {
        CachedCapabilities cached = cacheByModel.get(model);
        if (cached != null && System.currentTimeMillis() - cached.at < cached.ttl) {
            return cached.efforts;
        }
        synchronized (probeLocks.computeIfAbsent(model, k -> new Object())) {
            cached = cacheByModel.get(model);
            if (cached != null && System.currentTimeMillis() - cached.at < cached.ttl) {
                return cached.efforts;
            }
            // 顺序探测：本地反代是串行吞吐，7 连发并发会把排队请求挤出超时造成假模糊
            List<ProbeResult> results = ALL_EFFORTS.stream()
                    .map(effort -> probe(model, effort))
                    .toList();
            // 零星模糊（≤2 档，偶发排队/抖动掐出的假超时）再串行补测一次
            long ambiguous = results.stream().filter(r -> !r.definitive()).count();
            if (ambiguous > 0 && ambiguous <= 2) {
                results = results.stream()
                        .map(r -> r.definitive() ? r : probe(model, r.effort()))
                        .toList();
            }
            // 一个可判定的答案都没有 = 网关不可达/整体异常。此时全量兜底会让用户选到必然 400
            // 的档位（这正是"档位突然变多"事故的根因），改为返回默认子集且不缓存，网关恢复后重新探测
            if (results.stream().noneMatch(ProbeResult::definitive)) {
                log.warn("思考档位探测不可判定（网关 {} 对 {} 无有效响应），返回默认档位且不缓存", baseUrl, model);
                return DEFAULT_EFFORTS;
            }
            List<String> supported = results.stream()
                    .filter(ProbeResult::supported)
                    .map(ProbeResult::effort)
                    .toList();
            if (supported.isEmpty()) {
                // 模型对 reasoning_effort 全部明确拒绝：如实返回空，前端会隐藏/兜底档位选择
                log.info("模型 {} 明确不支持任何思考档位", model);
                cacheByModel.put(model, new CachedCapabilities(List.of(), System.currentTimeMillis(), CACHE_TTL_MS));
                return List.of();
            }
            log.info("模型 {} 支持的思考档位: {}", model, supported);
            // 有档位因超时/限流被剔除（非明确拒绝）：上游慢窗口所致，真实能力未知——
            // 缓存缩到 10 分钟让其尽快自愈；全部档位均有判定则按满 TTL 缓存
            boolean partial = results.stream().anyMatch(r -> !r.definitive());
            long ttl = partial ? CACHE_TTL_MS / 6 : CACHE_TTL_MS;
            cacheByModel.put(model, new CachedCapabilities(supported, System.currentTimeMillis(), ttl));
            return supported;
        }
    }

    private record CachedCapabilities(List<String> efforts, long at, long ttl) {
    }

    /**
     * 单档探测结果：supported=该档位是否可用；definitive=网关是否给出了可判定的答案
     * （200，或报错提到 reasoning 的 400——如 fledge 的 "reasoning_effort unsupported"、
     * step-5 的 "Reasoning is mandatory"，文案不一但都在谈 reasoning 能力）。
     * 与 reasoning 无关的 400（协议错误/参数缺失等）、限流/超时都是模糊结果。
     */
    private record ProbeResult(String effort, boolean supported, boolean definitive) {
    }

    private ProbeResult probe(String model, String effort) {
        String body = JacksonUtil.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "1")),
                "max_tokens", 1,
                "reasoning_effort", effort));
        try (HttpResponse resp = HttpRequest.post(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .timeout(PROBE_TIMEOUT_MS)
                .execute()) {
            log.debug("档位 {} 探测响应: HTTP {} / {}", effort, resp.getStatus(),
                    StrUtil.brief(resp.body(), 120));
            if (resp.getStatus() == 200) {
                return new ProbeResult(effort, true, true);
            }
            if (resp.getStatus() == 400 && resp.body().toLowerCase().contains("reasoning")) {
                return new ProbeResult(effort, false, true);
            }
            return new ProbeResult(effort, false, false);
        } catch (Exception e) {
            log.debug("档位 {} 探测异常: {}", effort, e.getMessage());
            return new ProbeResult(effort, false, false);
        }
    }
}
