package com.mio.ai.framework.zagent.stream;

import com.openai.core.http.Headers;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.SseException;
import com.openai.errors.UnexpectedStatusCodeException;

/**
 * 单次模型流式调用的失败分类（zcode failure-classifier 的精简版）。
 * <p>只判定"这次失败后原样重发同一请求是否有意义"：
 * 网络层断连、上游网关抖动、5xx/429 属瞬态可重试；
 * 鉴权/参数/模型不存在等确定性失败立即上抛，不浪费重试。
 * <p>服务端错误会提取 Retry-After 头（codex responses_retry 对位）：
 * 限流等待服从服务端要求，而不是本地指数退避猜。
 */
final class StreamFailureClassifier {

    /** Retry-After 超过该值视为不合理（网关误配），回落本地退避（codex：5 分钟上限） */
    private static final long MAX_HONORED_RETRY_AFTER_MS = 5 * 60_000;

    record Classification(boolean retryable, String reason, long retryAfterMs) {

        Classification(boolean retryable, String reason) {
            this(retryable, reason, 0);
        }

        static Classification fatal(String reason) {
            return new Classification(false, reason);
        }

        /**
         * 该失败是否归因于模型服务侧（上游网关/模型本身/凭证），而非用户本轮请求内容的问题。
         * <p>用于自愈：免费模型目录失效重建只应在"服务端这边的问题"时触发。
         * 上下文超限是用户侧输入过长（引擎走压缩重试），此处不算服务侧；
         * 模型不存在（404）与上游鉴权失败（401/403）属于目录陈旧，需重建以取回最新列表。
         */
        boolean modelSide() {
            return switch (reason) {
                case "upstream-stream-error", "rate-limited", "server-error-5xx",
                     "stream-idle-timeout", "empty-completion" -> true;
                default -> reason.startsWith("io-error:")
                        || reason.startsWith("model-unavailable")
                        || reason.startsWith("http-404")
                        || reason.startsWith("http-401")
                        || reason.startsWith("http-403")
                        || reason.startsWith("http-5");
            };
        }
    }

    static Classification classify(Throwable error) {
        // reactor 会把真实异常包进 cause 链（"#block terminated with an error"），
        // 沿链找第一个有意义的开放平台/IO 异常
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return Classification.fatal("interrupted");
            }
            if (current instanceof OpenAIServiceException serviceError) {
                Classification classification = classifyServiceError(serviceError);
                if (classification != null) {
                    return classification;
                }
            }
            if (current instanceof java.util.concurrent.TimeoutException) {
                // 流式 idle 超时（分片间隔超限）：半开连接的典型表现，原样重发有意义
                return new Classification(true, "stream-idle-timeout");
            }
            if (current instanceof java.io.IOException) {
                // 连接拒绝/重置/超时等全部网络层瞬态（okhttp 异常均继承自它）
                return new Classification(true, "io-error:" + current.getClass().getSimpleName());
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return Classification.fatal("unknown:" + error.getClass().getSimpleName());
    }

    /**
     * 自研协议适配器（AnthropicChatModel/ResponsesChatModel）不经 openai-java SDK，
     * 上游失败以裸 IllegalStateException("上游错误 HTTP &lt;code&gt;: &lt;body&gt;") 抛出，
     * 不进入 {@link #classify} 的 OpenAIServiceException 分支。这里按同一口径补判：
     * 5xx/429 可重试；4xx 若文本表明模型不可用则上报目录自愈，其余 4xx 直接失败。
     * <p>返回 null 表示不是适配器的上游错误形态，交由调用方按未知失败处理。
     */
    static Classification classifyVendorError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.startsWith("上游错误 HTTP ")) {
                int code = parseStatusCode(message);
                if (code >= 500 || code == 429) {
                    return new Classification(true, "http-" + code);
                }
                if (isModelUnavailable(message)) {
                    return Classification.fatal("model-unavailable-http-" + code + ":" + brief(message));
                }
                return Classification.fatal("http-" + code);
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    /** 从 "上游错误 HTTP 400: {...}" 提取状态码（解析失败回 0） */
    private static int parseStatusCode(String message) {
        int start = message.indexOf("上游错误 HTTP ");
        if (start < 0) {
            return 0;
        }
        start += "上游错误 HTTP ".length();
        int end = start;
        while (end < message.length() && Character.isDigit(message.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(message.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 开放平台服务错误的分类 + Retry-After 提取；其余服务错误返回 null 继续沿链找 */
    private static Classification classifyServiceError(OpenAIServiceException error) {
        long retryAfterMs = retryAfterFromHeaders(error.headers());
        if (error instanceof SseException sse) {
            // 反代以 SSE error 事件回传的上游失败（如 upstream stream failed），
            // 多为 zen 网关瞬态抖动
            return new Classification(true, "upstream-stream-error:" + sse.getMessage(), retryAfterMs);
        }
        if (error instanceof RateLimitException) {
            return new Classification(true, "rate-limited", retryAfterMs);
        }
        if (error instanceof InternalServerException) {
            return new Classification(true, "server-error-5xx", retryAfterMs);
        }
        // 带状态码的服务错误统一按码判定：
        // openai-java 的 BadRequest/NotFound/PermissionDenied 等都直接继承
        // OpenAIServiceException（彼此是兄弟而非父子），只认 UnexpectedStatusCodeException
        // 会把 400/404 落进 unknown 分支——这里改为按 statusCode() 判定。
        int code = statusCodeOf(error);
        if (code >= 500 || code == 429) {
            return new Classification(true, "http-" + code, retryAfterMs);
        }
        if (code > 0) {
            // 4xx 里的"模型不可用"类错误（模型不存在/未暴露该协议/已改名）说明
            // 我们手里的模型目录已陈旧：对下游按不可重试处理，同时上报自愈钩子重建目录
            if (isModelUnavailable(error.getMessage()) || isModelUnavailable(code + ":" + error.getMessage())) {
                return Classification.fatal("model-unavailable-http-" + code + ":"
                        + brief(error.getMessage()));
            }
            return Classification.fatal("http-" + code);
        }
        return null;
    }

    /**
     * 取服务错误的状态码：优先 UnexpectedStatusCodeException.statusCode()，
     * 其余子类（openai-java 各具体异常）用反射读同名的 statusCode()，
     * 避免为每个兄弟类型写一条分支。取不到回 0。
     */
    private static int statusCodeOf(OpenAIServiceException error) {
        if (error instanceof UnexpectedStatusCodeException unexpected) {
            return unexpected.statusCode();
        }
        try {
            var method = error.getClass().getMethod("statusCode");
            Object value = method.invoke(error);
            return value instanceof Integer code ? code : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 上游错误文本是否表明"这个模型不可用"（而非用户请求参数本身有问题）。
     * <p>opencode2api 对未暴露/已下线的模型回 400
     * （"the model uses an upstream protocol that opencode2api does not expose"）；
     * 其他网关常见表述一并覆盖。命中即认定目录陈旧，需重建。
     */
    private static boolean isModelUnavailable(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("model not found")
                || lower.contains("model_not_found")
                || lower.contains("unknown model")
                || lower.contains("no such model")
                || lower.contains("model does not exist")
                || lower.contains("does not expose")
                || lower.contains("not supported model")
                || lower.contains("unsupported model")
                || lower.contains("模型不存在")
                || lower.contains("模型不可用")
                || lower.contains("无可用模型");
    }

    /** 截断错误文本，避免把整段上游响应塞进日志与回调 reason */
    private static String brief(String message) {
        if (message == null) {
            return "";
        }
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() > 120 ? flat.substring(0, 120) : flat;
    }

    /** retry-after-ms（毫秒）优先，其次 retry-after（秒）：只在合理范围内采用（codex：≤5 分钟） */
    private static long retryAfterFromHeaders(Headers headers) {
        if (headers == null) {
            return 0;
        }
        try {
            var msValues = headers.values("retry-after-ms");
            if (msValues != null && !msValues.isEmpty()) {
                return clamp(Long.parseLong(msValues.get(0).trim()));
            }
            var secondValues = headers.values("retry-after");
            if (secondValues != null && !secondValues.isEmpty()) {
                return clamp(Long.parseLong(secondValues.get(0).trim()) * 1000);
            }
        } catch (NumberFormatException ignored) {
            // 头格式异常：当作没有
        }
        return 0;
    }

    private static long clamp(long ms) {
        return ms <= 0 || ms > MAX_HONORED_RETRY_AFTER_MS ? 0 : ms;
    }

    private StreamFailureClassifier() {
    }
}
