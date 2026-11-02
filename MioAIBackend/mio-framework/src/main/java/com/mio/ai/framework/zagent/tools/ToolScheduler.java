package com.mio.ai.framework.zagent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 工具调度器（zcode ToolScheduler 的对位移植）：
 * 连续的只读并发安全工具并入并行组（上限 10），其余串行执行。
 * <p>委派类工具走 {@link DelegationExecutors} 专用池：它们长阻塞且会再开子运行时，
 * 与毫秒级普通工具同池会互相饿死（父级委派占满池 → 子代理并行工具无线程可用）。
 */
public final class ToolScheduler {

    private static final int MAX_CONCURRENCY = 10;

    /**
     * 全引擎共享的并行工具池：调度器本身无状态，每轮 new 一个实例只是借池干活；
     * 不能每轮建池——旧实现每轮/每子代理泄漏 10 个常驻线程，长跑进程线程数无限增长。
     */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(MAX_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "zagent-tool-parallel");
        thread.setDaemon(true);
        return thread;
    });

    private final ToolRegistry registry;

    public ToolScheduler(ToolRegistry registry) {
        this.registry = registry;
    }

    /** 执行一批调用，保持调用顺序返回结果 */
    public List<ToolRegistry.Executed> executeAll(List<ToolRegistry.PendingCall> calls) {
        List<ToolRegistry.Executed> results = new ArrayList<>(calls.size());
        int i = 0;
        while (i < calls.size()) {
            int j = i;
            while (j < calls.size() && isParallelizable(calls.get(j))) {
                j++;
            }
            if (j > i) {
                results.addAll(runParallel(calls.subList(i, j)));
                i = j;
            } else {
                results.add(registry.execute(calls.get(i)));
                i++;
            }
        }
        return results;
    }

    private boolean isParallelizable(ToolRegistry.PendingCall call) {
        ToolEntry entry = registry.entry(call.name());
        return entry != null && entry.parallelizable();
    }

    /**
     * 并行执行一组调用，结果严格按入参顺序返回——{@code AgentEngine.executeToolBatch}
     * 按位置索引回填 tool_call id，顺序错位会让模型看到错配的工具结果。
     */
    private List<ToolRegistry.Executed> runParallel(List<ToolRegistry.PendingCall> calls) {
        ToolRegistry.Executed[] ordered = new ToolRegistry.Executed[calls.size()];
        List<Future<ToolRegistry.Executed>> futures = new ArrayList<>();
        List<Integer> futureSlots = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            ToolRegistry.PendingCall call = calls.get(i);
            ExecutorService pool = isDelegation(call) ? DelegationExecutors.foreground() : POOL;
            try {
                futures.add(pool.submit(() -> registry.execute(call)));
                futureSlots.add(i);
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                // 有界池满：单个工具降级为失败信封，绝不能让提交异常穿透调度器中断整轮。
                // （旧实现用的是无界队列的池，提交永不拒绝，这是换有界池后必须补的分支）
                ordered[i] = new ToolRegistry.Executed(call.name(),
                        "<tool_use_error>" + call.name() + " was not executed: too many tools are "
                                + "running concurrently. Retry it on its own.</tool_use_error>",
                        true, 0);
            }
        }
        for (int k = 0; k < futures.size(); k++) {
            int slot = futureSlots.get(k);
            try {
                ordered[slot] = futures.get(k).get();
            } catch (Exception e) {
                ordered[slot] = new ToolRegistry.Executed(calls.get(slot).name(),
                        "<tool_use_error>Parallel tool execution failed: " + e.getMessage()
                                + "</tool_use_error>", true, 0);
            }
        }
        return new ArrayList<>(java.util.Arrays.asList(ordered));
    }

    private boolean isDelegation(ToolRegistry.PendingCall call) {
        ToolEntry entry = registry.entry(call.name());
        return entry != null && entry.delegation();
    }

    public void shutdown() {
        // 共享池不随实例关闭（进程级生命周期）
    }
}
