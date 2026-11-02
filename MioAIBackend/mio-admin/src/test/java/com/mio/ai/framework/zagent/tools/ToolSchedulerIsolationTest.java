package com.mio.ai.framework.zagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工具调度回归测试：委派与普通工具必须走不同线程池。
 * <p>此前二者共用一个 10 线程池，父级委派占满后子代理自己的并行工具拿不到线程，
 * 表现为"莫名其妙变慢"甚至长时间停住。
 * <p>注意：断言必须经由 {@link ToolScheduler#executeAll} 触发；直接调
 * {@code registry.execute} 会绕过池路由，测不到真实行为（本测试首版曾因此漏检，
 * 用变异验证才发现）。
 */
class ToolSchedulerIsolationTest {

    /** 共享池容量，与 ToolScheduler.MAX_CONCURRENCY 一致 */
    private static final int SHARED_POOL_SIZE = 10;

    private static ToolContext context() {
        return new ToolContext("chat-isolation", null, null, null, null, null, null, null);
    }

    @Test
    void delegationIsNotStarvedWhenSharedPoolIsSaturated() throws Exception {
        CountDownLatch entered = new CountDownLatch(SHARED_POOL_SIZE);
        CountDownLatch release = new CountDownLatch(1);
        ToolRegistry registry = new ToolRegistry(context());
        registry.register(ToolEntry.ofReadOnly("Blocker", "blocks until released", "{}",
                (ToolEntry.StringHandler) (input, ctx) -> {
                    entered.countDown();
                    try {
                        release.await(15, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "released";
                }));
        // 委派工具执行体立即返回：被测的是"能否拿到线程"，不是执行时长
        registry.register(ToolEntry.ofDelegation("Agent", "delegate", "{}", 10_000,
                (ToolEntry.StringHandler) (input, ctx) -> "delegated-ok"));
        ToolScheduler scheduler = new ToolScheduler(registry);
        Thread pressure = null;
        try {
            // 一次提交 10 个阻塞只读工具 → 占满共享池的 10 个线程
            List<ToolRegistry.PendingCall> blockers = new ArrayList<>();
            for (int i = 0; i < SHARED_POOL_SIZE; i++) {
                blockers.add(new ToolRegistry.PendingCall("b" + i, "Blocker", "{}"));
            }
            AtomicReference<List<ToolRegistry.Executed>> ignored = new AtomicReference<>();
            pressure = new Thread(() -> ignored.set(scheduler.executeAll(blockers)), "test-pressure");
            pressure.setDaemon(true);
            pressure.start();
            Assertions.assertTrue(entered.await(10, TimeUnit.SECONDS), "共享池应已被阻塞工具占满");

            // 关键：委派同样经 executeAll 提交，走同一池路由逻辑，且必须立刻完成
            long startedAt = System.nanoTime();
            List<ToolRegistry.Executed> results = scheduler.executeAll(
                    List.of(new ToolRegistry.PendingCall("d1", "Agent",
                            "{\"description\":\"x\",\"prompt\":\"y\"}")));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            Assertions.assertEquals(1, results.size());
            Assertions.assertFalse(results.get(0).error(),
                    "共享池占满时委派应仍可执行: " + results.get(0).content());
            Assertions.assertEquals("delegated-ok", results.get(0).content());
            // 若委派也走共享池，它必须等 release 才有线程（≥15s 或直到超时）
            Assertions.assertTrue(elapsedMs < 5_000,
                    "委派必须与共享池隔离，实测耗时 " + elapsedMs + "ms");
        } finally {
            release.countDown();
            if (pressure != null) {
                pressure.join(15_000);
            }
            registry.shutdown();
        }
    }

    @Test
    void parallelResultsKeepCallOrder() {
        // 只读工具并行执行，但结果必须严格按入参顺序返回：
        // AgentEngine 按位置索引回填 tool_call id，顺序错位会造成结果错配
        ToolRegistry registry = new ToolRegistry(context());
        registry.register(ToolEntry.ofReadOnly("Slow", "slow", "{}",
                (ToolEntry.StringHandler) (input, ctx) -> {
                    sleep(120);
                    return "slow";
                }));
        registry.register(ToolEntry.ofReadOnly("Fast", "fast", "{}",
                (ToolEntry.StringHandler) (input, ctx) -> "fast"));
        ToolScheduler scheduler = new ToolScheduler(registry);
        try {
            List<ToolRegistry.PendingCall> calls = List.of(
                    new ToolRegistry.PendingCall("a", "Slow", "{}"),
                    new ToolRegistry.PendingCall("b", "Fast", "{}"),
                    new ToolRegistry.PendingCall("c", "Slow", "{}"),
                    new ToolRegistry.PendingCall("d", "Fast", "{}"));
            List<ToolRegistry.Executed> results = scheduler.executeAll(calls);

            Assertions.assertEquals(4, results.size());
            Assertions.assertEquals(List.of("slow", "fast", "slow", "fast"),
                    results.stream().map(ToolRegistry.Executed::content).toList());
            Assertions.assertEquals(List.of("Slow", "Fast", "Slow", "Fast"),
                    results.stream().map(ToolRegistry.Executed::toolName).toList());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void mixedBatchStillReturnsOneResultPerCall() {
        // 串行段与并行段混合：结果数量与顺序都必须与入参一一对应
        ToolRegistry registry = new ToolRegistry(context());
        registry.register(ToolEntry.ofReadOnly("Read1", "read only", "{}",
                (ToolEntry.StringHandler) (input, ctx) -> "r"));
        registry.register(ToolEntry.ofMutable("Write1", "mutable", "{}", 5_000,
                (ToolEntry.StringHandler) (input, ctx) -> "w"));
        ToolScheduler scheduler = new ToolScheduler(registry);
        try {
            List<ToolRegistry.PendingCall> calls = List.of(
                    new ToolRegistry.PendingCall("1", "Read1", "{}"),
                    new ToolRegistry.PendingCall("2", "Read1", "{}"),
                    new ToolRegistry.PendingCall("3", "Write1", "{}"),
                    new ToolRegistry.PendingCall("4", "Read1", "{}"));
            List<ToolRegistry.Executed> results = scheduler.executeAll(calls);

            Assertions.assertEquals(4, results.size());
            Assertions.assertEquals(List.of("r", "r", "w", "r"),
                    results.stream().map(ToolRegistry.Executed::content).toList());
            Assertions.assertEquals(List.of("Read1", "Read1", "Write1", "Read1"),
                    results.stream().map(ToolRegistry.Executed::toolName).toList());
        } finally {
            registry.shutdown();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
