package com.mio.ai.bot.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 供应商模型连通性测试：按 API 格式对真实端点发一次最小请求（1 token 级），
 * 返回可达性与延迟。三种格式：openai chat completions / anthropic messages / responses。
 * <p>头注入与引擎调用方一致：浏览器 UA + x-opencode-session（opencode 网关按会话路由，
 * 缺失会被 400 MissingSessionID 拒绝，对其他端点无害）；opencode.ai 端点补 x-api-key
 * （zen go 网关的工作区 key 认证头）。
 */
@Slf4j
@Component
public class ModelConnectivityTester {

    private static final int TIMEOUT_MS = 20_000;

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";

    public record TestResult(boolean ok, long latencyMs, String message) {
    }

    public TestResult test(String baseUrl, String apiKey, String apiFormat, String model) {
        HttpRequest request = switch (apiFormat) {
            case "anthropic" -> anthropicRequest(baseUrl, apiKey, model);
            case "responses" -> responsesRequest(baseUrl, apiKey, model);
            default -> openaiRequest(baseUrl, apiKey, model);
        };
        request.header("User-Agent", BROWSER_UA);
        request.header("x-opencode-session", "mioai-connectivity-test");
        if (baseUrl.contains("opencode.ai")) {
            request.header("x-api-key", apiKey);
        }
        long start = System.currentTimeMillis();
        try (HttpResponse resp = request.timeout(TIMEOUT_MS).execute()) {
            long latency = System.currentTimeMillis() - start;
            if (resp.getStatus() >= 200 && resp.getStatus() < 300) {
                return new TestResult(true, latency, "连通正常（" + latency + "ms）");
            }
            String brief = cn.hutool.core.util.StrUtil.brief(resp.body(), 200);
            return new TestResult(false, latency, describeFailure(resp.getStatus(), brief));
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.info("连通性测试失败: {} {} -> {}", apiFormat, baseUrl, e.getMessage());
            return new TestResult(false, latency, latency >= TIMEOUT_MS
                    ? "连接超时（" + TIMEOUT_MS / 1000 + "s），请检查 BaseURL 是否可达、拼写是否正确"
                    : "连接失败：" + e.getMessage());
        }
    }

    /** Chat Completions：POST {base}/chat/completions，Bearer 鉴权 */
    private HttpRequest openaiRequest(String baseUrl, String apiKey, String model) {
        return HttpRequest.post(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(JacksonUtil.writeValueAsString(Map.of(
                        "model", model, "max_tokens", 1,
                        "messages", List.of(Map.of("role", "user", "content", "hi")))));
    }

    /** Anthropic Messages：POST {base}/v1/messages，x-api-key + anthropic-version 头 */
    private HttpRequest anthropicRequest(String baseUrl, String apiKey, String model) {
        String url = baseUrl.endsWith("/v1") ? baseUrl + "/messages" : baseUrl + "/v1/messages";
        return HttpRequest.post(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(JacksonUtil.writeValueAsString(Map.of(
                        "model", model, "max_tokens", 1,
                        "messages", List.of(Map.of("role", "user", "content", "hi")))));
    }

    /** Responses：POST {base}/responses，Bearer 鉴权 */
    private HttpRequest responsesRequest(String baseUrl, String apiKey, String model) {
        return HttpRequest.post(baseUrl + "/responses")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(JacksonUtil.writeValueAsString(Map.of(
                        "model", model, "input", "hi", "max_output_tokens", 16)));
    }

    private String describeFailure(int status, String body) {
        return switch (status) {
            case 400 -> "请求被拒（HTTP 400）：" + body;
            case 401, 403 -> "认证失败（HTTP " + status + "）：" + body;
            case 404 -> "端点不存在（HTTP 404），请检查 BaseURL 与 API 格式是否匹配";
            case 429 -> "限流（HTTP 429）：" + body;
            default -> "HTTP " + status + "：" + body;
        };
    }
}
