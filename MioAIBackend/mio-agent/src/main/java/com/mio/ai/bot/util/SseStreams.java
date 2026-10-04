package com.mio.ai.bot.util;

import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.bot.model.dto.SseChunk;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型流 → SseEmitter 的统一推送管道。
 *
 * 所有事件按 {@link SseChunk} 序列化为一行 JSON 信封（换行被转义，
 * 天然规避 SSE data 剥空格问题），并附带递增 seq 与毫秒时间戳。
 *
 * 发送失败（客户端断开、连接超时）时只停止推送，不取消上游 Flux：
 * 记忆落库等 doOnComplete/doOnTerminate 收尾逻辑照常执行，
 * 刷新会话即可看到完整回答。
 */
public final class SseStreams {

    /**
     * 聊天流超时：0 = 不设墙钟上限。多步 Agent 任务时长无上界
     * （N 步 × 单步流式上限 + 工具执行时间），固定上限会在任务中途干净掐断连接
     * （表现为无 error 事件的静默断流）。连接存活由 15s 心跳维持，
     * 挂死由前端看门狗（60s 无事件）判定，收尾由任务结束时的 complete 兜底。
     */
    public static final long CHAT_TIMEOUT_MS = 0L;

    private SseStreams() {
    }

    /** 把模型分块流推送为信封事件，流结束后追加 done 事件 */
    public static void pipe(Flux<SseChunk> flux, SseEmitter emitter) {
        AtomicBoolean dead = new AtomicBoolean(false);
        AtomicInteger seq = new AtomicInteger();
        flux.subscribe(
                chunk -> {
                    if (dead.get()) {
                        return;
                    }
                    sendTyped(emitter, chunk.fields(), seq.incrementAndGet(), dead);
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
                        sendTyped(emitter, SseChunk.done().fields(), seq.incrementAndGet(), dead);
                    }
                    try {
                        emitter.complete();
                    } catch (IllegalStateException ignored) {
                        // 连接已被容器关闭
                    }
                }
        );
    }

    /**
     * 发送一条信封事件（供代理类逐步发送使用）
     *
     * @return 是否发送成功；连接已断开时返回 false
     */
    public static boolean sendTyped(SseEmitter emitter, Map<String, Object> fields) {
        return sendTyped(emitter, fields, null, new AtomicBoolean(false));
    }

    /** 同上，附带调用方维护的递增序号 */
    public static boolean sendTyped(SseEmitter emitter, Map<String, Object> fields, int seq) {
        return sendTyped(emitter, fields, seq, new AtomicBoolean(false));
    }

    private static boolean sendTyped(SseEmitter emitter, Map<String, Object> fields, Integer seq, AtomicBoolean dead) {
        Map<String, Object> envelope = new LinkedHashMap<>(fields);
        envelope.putIfAbsent("seq", seq == null ? 0 : seq);
        envelope.put("ts", System.currentTimeMillis());
        try {
            emitter.send(JacksonUtil.writeValueAsString(envelope));
            return true;
        } catch (IOException | IllegalStateException e) {
            // 连接已断/已超时：停止推送但保持上游订阅，收尾落库照常
            dead.set(true);
            return false;
        }
    }
}
