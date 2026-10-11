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
        if (error instanceof UnexpectedStatusCodeException unexpected) {
            int code = unexpected.statusCode();
            if (code >= 500 || code == 429) {
                return new Classification(true, "http-" + code, retryAfterMs);
            }
            return Classification.fatal("http-" + code);
        }
        return null;
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
