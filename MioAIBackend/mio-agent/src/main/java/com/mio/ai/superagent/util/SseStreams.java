package com.mio.ai.superagent.util;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型流 → SseEmitter 的统一推送管道。
 *
 * 发送失败（客户端断开、连接超时）时只停止推送，不取消上游 Flux：
 * 记忆落库等 doOnComplete/doOnTerminate 收尾逻辑照常执行，
 * 刷新会话即可看到完整回答。
 */
public final class SseStreams {

    /** 聊天流超时：10 分钟。正常回答远短于此，仅兜底连接挂死 */
    public static final long CHAT_TIMEOUT_MS = 600_000L;

    private SseStreams() {
    }

    /** 每行垫一个前导空格，抵消浏览器对 "data:" 后单个空格的剥离 */
    private static String padSseData(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }
        return " " + chunk.replace("\n", "\n ");
    }

    public static void pipe(Flux<String> flux, SseEmitter emitter) {
        AtomicBoolean dead = new AtomicBoolean(false);
        flux.subscribe(
                chunk -> {
                    if (dead.get()) {
                        return;
                    }
                    try {
                        // SSE 规范：浏览器剥掉 "data:" 后的一个空格。Spring 不写保护性空格，
                        // 导致以空格开头的 chunk（如 "### 标题" 切分后的 " 标题"）丢空格，
                        // 前台 markdown 结构被破坏。这里给每行垫一个空格，被剥掉的正好是垫层。
                        emitter.send(padSseData(chunk));
                    } catch (IOException | IllegalStateException e) {
                        dead.set(true);
                    }
                },
                err -> {
                    if (dead.get()) {
                        return;
                    }
                    try {
                        emitter.completeWithError(err);
                    } catch (IllegalStateException ignored) {
                        // 连接已被容器关闭
                    }
                },
                () -> {
                    if (!dead.get()) {
                        try {
                            emitter.send("[DONE]");
                        } catch (IOException | IllegalStateException ignored) {
                            // 连接已断开
                        }
                    }
                    try {
                        emitter.complete();
                    } catch (IllegalStateException ignored) {
                        // 连接已被容器关闭
                    }
                }
        );
    }
}
