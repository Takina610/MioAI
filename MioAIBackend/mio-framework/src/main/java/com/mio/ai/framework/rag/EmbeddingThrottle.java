package com.mio.ai.framework.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 嵌入请求全局节流。向量化与查询嵌入共享同一个 RPM 限额，
 * 两侧都必须经过这里排队，保证任意相邻两次嵌入请求的发起间隔不低于配置值。
 * <p>发放权必须在临界区内串行领取：先占住下一个时间槽再出锁睡眠，
 * 否则并发线程会读到同一个上次请求时间同时放行，限流被并发击穿。
 */
@Slf4j
@Component
public class EmbeddingThrottle {

    private final long intervalMs;

    private final Object grantLock = new Object();

    private long nextAllowedAt = 0L;

    public EmbeddingThrottle(@Value("${mio.ai.rag.embed-min-interval-ms:12500}") long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /**
     * 阻塞直到本线程获得一次嵌入请求的发起权
     */
    public void awaitTurn() {
        long wait;
        synchronized (grantLock) {
            long now = System.currentTimeMillis();
            wait = nextAllowedAt - now;
            nextAllowedAt = Math.max(now, nextAllowedAt) + intervalMs;
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("嵌入节流等待被中断");
            }
        }
    }
}
