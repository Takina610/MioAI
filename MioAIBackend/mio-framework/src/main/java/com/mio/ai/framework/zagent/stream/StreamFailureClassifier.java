package com.mio.ai.framework.zagent.stream;

import com.openai.errors.InternalServerException;
import com.openai.errors.RateLimitException;
import com.openai.errors.SseException;
import com.openai.errors.UnexpectedStatusCodeException;

/**
 * 单次模型流式调用的失败分类（zcode failure-classifier 的精简版）。
 * <p>只判定"这次失败后原样重发同一请求是否有意义"：
 * 网络层断连、上游网关抖动、5xx/429 属瞬态可重试；
 * 鉴权/参数/模型不存在等确定性失败立即上抛，不浪费重试。
 */
final class StreamFailureClassifier {

    record Classification(boolean retryable, String reason) {
        static Classification fatal(String reason) {
            return new Classification(false, reason);
        }
    }

    static Classification classify(Throwable error) {
        // reactor 会把真实异常包进 cause 链（"#block terminated with an error"），
        // 沿链找第一个有意义的开放平台/IO 异常
        Throwable current = error;
        boolean interrupted = false;
        while (current != null) {
            interrupted = interrupted || current instanceof InterruptedException;
            if (current instanceof InterruptedException) {
                return Classification.fatal("interrupted");
            }
            if (current instanceof SseException sse) {
                // 反代以 SSE error 事件回传的上游失败（如 upstream stream failed），
                // 多为 zen 网关瞬态抖动
                return new Classification(true, "upstream-stream-error:" + sse.getMessage());
            }
            if (current instanceof RateLimitException) {
                return new Classification(true, "rate-limited");
            }
            if (current instanceof InternalServerException) {
                return new Classification(true, "server-error-5xx");
            }
            if (current instanceof UnexpectedStatusCodeException unexpected) {
                int code = unexpected.statusCode();
                if (code >= 500 || code == 429) {
                    return new Classification(true, "http-" + code);
                }
                return Classification.fatal("http-" + code);
            }
            if (current instanceof java.io.IOException) {
                // 连接拒绝/重置/超时等全部网络层瞬态（okhttp 异常均继承自它）
                return new Classification(true, "io-error:" + current.getClass().getSimpleName());
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return Classification.fatal("unknown:" + error.getClass().getSimpleName());
    }

    private StreamFailureClassifier() {
    }
}
