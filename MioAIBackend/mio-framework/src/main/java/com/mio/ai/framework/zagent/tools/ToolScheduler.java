package com.mio.ai.framework.zagent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 工具调度器（zcode ToolScheduler 的对位移植）：
 * 连续的只读并发安全工具并入并行组（上限 10），其余串行执行。
 */
public final class ToolScheduler {

    private static final int MAX_CONCURRENCY = 10;

    private final ToolRegistry registry;
    private final ExecutorService pool;

    public ToolScheduler(ToolRegistry registry) {
        this.registry = registry;
        this.pool = Executors.newFixedThreadPool(MAX_CONCURRENCY, runnable -> {
            Thread thread = new Thread(runnable, "zagent-tool-parallel");
            thread.setDaemon(true);
            return thread;
        });
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
        return registry.entries().stream()
                .filter(entry -> entry.name.equals(call.name()))
                .findFirst().map(ToolEntry::parallelizable)
                .orElse(false);
    }

    private List<ToolRegistry.Executed> runParallel(List<ToolRegistry.PendingCall> calls) {
        List<ToolRegistry.Executed> results = new ArrayList<>(calls.size());
        List<Future<ToolRegistry.Executed>> futures = new ArrayList<>();
        for (ToolRegistry.PendingCall call : calls) {
            futures.add(pool.submit(() -> registry.execute(call)));
        }
        for (Future<ToolRegistry.Executed> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                results.add(new ToolRegistry.Executed("unknown",
                        "<tool_use_error>Parallel tool execution failed: " + e.getMessage() + "</tool_use_error>",
                        true, 0));
            }
        }
        return results;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
