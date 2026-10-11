package com.mio.ai.bot.agent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天轮次专用执行池：所有 Agent 轮次任务（含单条兜底回复）跑在这里，
 * 不占用 ForkJoinPool.commonPool——commonPool 被阻塞任务占满会饿死整个 JVM 的 CompletableFuture。
 * <p>池满时拒绝并让调用方回一条"系统繁忙"的 SSE，而不是排队把内存撑爆。
 */
public final class TurnExecutors {

    /** 常驻线程数；突发时最多扩到 MAX，空闲 60s 回收 */
    private static final int CORE = 8;
    private static final int MAX = 32;

    private static final ExecutorService POOL = new ThreadPoolExecutor(
            CORE, MAX, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    static {
        ((ThreadPoolExecutor) POOL).allowCoreThreadTimeOut(true);
    }

    private static final class ThreadFactory implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "mio-bot-turn-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 提交轮次任务；池满返回 false（调用方负责向用户回"系统繁忙"，不排队、不丢弃静默）。
     */
    public static boolean execute(Runnable task) {
        try {
            POOL.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            return false;
        }
    }

    private TurnExecutors() {
    }
}
