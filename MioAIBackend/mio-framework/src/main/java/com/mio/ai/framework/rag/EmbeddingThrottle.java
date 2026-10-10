package com.mio.ai.framework.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 嵌入请求全局节流。向量化与查询嵌入共享同一个 RPM 限额，
 * 两侧都必须经过这里排队，保证任意相邻两次嵌入请求的间隔不低于配置值。
 */
@Slf4j
@Component
public class EmbeddingThrottle {

    private final long intervalMs;

    private final AtomicLong lastRequestAt = new AtomicLong(0);

    public EmbeddingThrottle(@Value("${mio.ai.rag.embed-min-interval-ms:12500}") long intervalMs) {
        this.intervalMs = intervalMs;
    }

    /**
     * 阻塞直到本线程获得一次嵌入请求的发起权
     */
    public void awaitTurn() {
        long now = System.currentTimeMillis();
        long earliest = lastRequestAt.get() + intervalMs;
        long wait = earliest - now;
        try {
            if (wait > 0) {
                Thread.sleep(wait);
                lastRequestAt.set(earliest);
            } else {
                lastRequestAt.set(now);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("嵌入节流等待被中断");
        }
    }
}
