package com.mio.ai.framework.zagent.subagent;

import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.task.BackgroundTasks;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台委派墙钟预算（看门狗）回归。
 * <p>后台委派没有等待者，若子代理卡住会永久占住有界池的一个线程，所以
 * {@code Subagents.runWithBudget} 必须自起看门狗主动中断执行线程。两个用例分别守护：
 * ①预算到点后任务以 failed 收尾（不能永久 running）；
 * ②返回瞬间中断标记已被清除——残留会让复用的池线程在下一步立刻自杀。
 * <p>中断标记为何要直接驱动 {@code runWithBudget}：JDK 的 ThreadPoolExecutor 在任务
 * 边界会自动清标记，从池外永远观察不到生产代码那一行是否生效（首版测试因此假通过，
 * 变异验证才发现）。
 */
class SubagentsBackgroundBudgetTest {

    /** 预算：足够短以便快速触发看门狗，又足够长以免构造期抖动导致误判 */
    private static final long BUDGET_MS = 300;

    @AfterEach
    void restorePool() {
        Subagents.useBackgroundExecutor(null);
    }

    private static ExecutorService singleThreadPool() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "test-bg-budget");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Subagents runnerWith(BackgroundTasks tasks, ToolsetFactory factory) {
        ToolsetFactory.RunContext runContext = new ToolsetFactory.RunContext(
                "chat-budget", null, List.of(), tasks);
        AgentEngineConfig config = new AgentEngineConfig(100, 1800, 3, 200_000, BUDGET_MS, 4);
        return new Subagents(Mockito.mock(ChatModel.class), config, null,
                factory, runContext, Mockito.mock(AgentEvents.class), "test-model");
    }

    /**
     * 子运行唯一的注册表入口是 7 参 build（hooks + 无 ownerKey 版本）。
     * 让它在可中断的等待上阻塞：看门狗中断时抛带 InterruptedException cause 的运行时异常，
     * 这正是 runWithBudget 的 isInterrupted 分支要识别的形态。
     */
    private static ToolsetFactory blockingFactory(CountDownLatch entered) {
        ToolsetFactory factory = Mockito.mock(ToolsetFactory.class);
        Mockito.when(factory.build(Mockito.anyString(), Mockito.any(), Mockito.anyList(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        // 还原中断标记并包成运行时异常：模拟真实阻塞工具被 cancel(true) 打断
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("registry build interrupted mid-assembly", e);
                    }
                    return null;
                });
        return factory;
    }

    private static void awaitTerminal(BackgroundTasks.Task task, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && "running".equals(task.status)) {
            Thread.sleep(25);
        }
    }

    @Test
    void budgetWatchdogClosesOutTaskAsFailed() throws Exception {
        ExecutorService pool = singleThreadPool();
        Subagents.useBackgroundExecutor(pool);
        BackgroundTasks tasks = BackgroundTasks.instance();
        CountDownLatch entered = new CountDownLatch(1);
        Subagents runner = runnerWith(tasks, blockingFactory(entered));

        try {
            Subagents.Result launched = runner.launch("general-purpose", "budget case", "prompt", true);
            BackgroundTasks.Task task = tasks.get(launched.backgroundTaskId())
                    .orElseThrow(() -> new AssertionError("后台任务应已登记"));

            Assertions.assertTrue(entered.await(5, TimeUnit.SECONDS),
                    "子运行应已进入注册表装配（阻塞点）");

            awaitTerminal(task, 10_000);

            Assertions.assertNotEquals("running", task.status,
                    "预算耗尽后任务必须收尾，不能永久 running");
            Assertions.assertEquals("failed", task.status,
                    "预算耗尽应以失败收尾: " + task.output);
            Assertions.assertTrue(task.output.contains("time budget"),
                    "失败原因应说明预算耗尽: " + task.output);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 直接驱动 runWithBudget：body 在场景规定的时刻被看门狗中断，返回后立刻检查
     * 当前线程的中断标记。这一步必须在 runWithBudget 返回的同一线程上做，
     * 越过池的任务边界后标记已被 JDK 清掉，测不到生产代码的行为。
     */
    @Test
    void runWithBudgetClearsInterruptFlagBeforeReturning() throws Exception {
        AtomicBoolean interruptedAfterReturn = new AtomicBoolean(true);
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService worker = singleThreadPool();
        Subagents runner = runnerWith(BackgroundTasks.instance(), Mockito.mock(ToolsetFactory.class));
        try {
            worker.execute(() -> {
                try {
                    runner.runWithBudget(() -> {
                        try {
                            Thread.sleep(60_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("interrupted by watchdog", e);
                        }
                        return new Subagents.Outcome("unreachable", 0, 0, 0, false);
                    });
                    // runWithBudget 已返回：此处是观察清理行为的唯一时机
                    interruptedAfterReturn.set(Thread.currentThread().isInterrupted());
                } finally {
                    done.countDown();
                }
            });

            Assertions.assertTrue(done.await(10, TimeUnit.SECONDS),
                    "看门狗应在预算内中断 body 并让 runWithBudget 返回");
            Assertions.assertFalse(interruptedAfterReturn.get(),
                    "runWithBudget 返回前必须清除中断标记，否则复用的池线程会立即失败");
        } finally {
            worker.shutdownNow();
        }
    }
}
