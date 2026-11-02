package com.mio.ai.framework.zagent.tools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 委派执行池（子代理/群成员）。
 * <p>与 {@link ToolScheduler} 的普通工具池分离的两个理由：
 * <ol>
 *   <li>委派调用是长阻塞的（一次子代理可能跑几十步模型），与毫秒级的文件/搜索工具
 *       同池会让"父级委派占满池 → 子代理自己的并行工具拿不到线程"相互饿死；</li>
 *   <li>委派线程数必须可界：此前后台子代理用无界 cachedThreadPool，每个线程都能跑
 *       完整 AgentEngine，多轮累积会把线程数推到不可控。</li>
 * </ol>
 * 池满时抛出 {@link RejectedExecutionException}，由调用方转成结构化失败告知模型，
 * 而不是静默排队把内存与线程撑爆。
 */
public final class DelegationExecutors {

    /** 前台委派并发上限（模型一轮内可并行发起的委派数不会超过它，超出排队） */
    private static final int FOREGROUND_MAX = 8;
    private static final int FOREGROUND_QUEUE = 32;

    /** 后台委派并发上限（run_in_background 的子代理） */
    private static final int BACKGROUND_MAX = 4;
    private static final int BACKGROUND_QUEUE = 32;

    private static final ExecutorService FOREGROUND = newPool(
            "zagent-delegate", FOREGROUND_MAX, FOREGROUND_QUEUE);
    private static final ExecutorService BACKGROUND = newPool(
            "zagent-delegate-bg", BACKGROUND_MAX, BACKGROUND_QUEUE);

    private static ExecutorService newPool(String namePrefix, int threads, int queueCapacity) {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, namePrefix + "-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 前台委派池：调用方阻塞等待结果 */
    public static ExecutorService foreground() {
        return FOREGROUND;
    }

    /** 后台委派池：提交即返回，结果经任务注册表通知 */
    public static ExecutorService background() {
        return BACKGROUND;
    }

    public static boolean isRejection(Throwable error) {
        return error instanceof RejectedExecutionException;
    }

    private DelegationExecutors() {
    }
}
