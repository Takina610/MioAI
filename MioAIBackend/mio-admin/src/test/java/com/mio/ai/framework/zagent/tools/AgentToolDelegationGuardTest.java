package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.subagent.SubagentLauncher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 工具的委派护栏回归测试。
 * <p>覆盖三个此前缺失的约束：①委派有墙钟预算，超时以结构化失败回给模型而不是无限挂起；
 * ②单轮并行委派有配额，超出快速失败而不是全部压进队列；③子代理失败带结构化标记
 * （主控据此可重试/换人），不再只是一段看起来像正常输出的报错文本。
 */
class AgentToolDelegationGuardTest {

    /** 永不返回的委派桩：模拟子代理多步串行、总时长远超单步 idle 超时的情形 */
    private static final class HangingLauncher implements SubagentLauncher {
        private final CountDownLatch interrupted = new CountDownLatch(1);

        @Override
        public Result launch(String subagentType, String description, String prompt, boolean background) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return new Result("agent_x", "general-purpose", prompt, "never", 0, 0, 0, null);
        }
    }

    /** 立即失败的委派桩 */
    private static final class FailingLauncher implements SubagentLauncher {
        @Override
        public Result launch(String subagentType, String description, String prompt, boolean background) {
            return new Result("agent_y", "member-a", prompt, "(Subagent failed: boom)", 0, 0, 0, null, true);
        }
    }

    /** 慢速但正常的委派桩：用于并发配额断言 */
    private static final class SlowLauncher implements SubagentLauncher {
        private final AtomicInteger running = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final long holdMs;

        SlowLauncher(long holdMs) {
            this.holdMs = holdMs;
        }

        @Override
        public Result launch(String subagentType, String description, String prompt, boolean background) {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(holdMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            return new Result("agent_z", "member-a", prompt, "done", 0, 0, 0, null);
        }
    }

    private static ToolRegistry registry(SubagentLauncher launcher, long timeoutMs, int maxConcurrent) {
        ToolContext context = new ToolContext("chat-1", null, null, null,
                null, launcher, null, null);
        ToolRegistry registry = new ToolRegistry(context);
        registry.register(new AgentTool(launcher, timeoutMs, maxConcurrent).entry());
        return registry;
    }

    private static String call(String prompt) {
        return "{\"description\":\"t\",\"prompt\":\"" + prompt + "\",\"subagent_type\":\"member-a\"}";
    }

    @Test
    void delegationTimesOutWithStructuredFailureInsteadOfHangingForever() {
        HangingLauncher launcher = new HangingLauncher();
        ToolRegistry registry = registry(launcher, 300, 0);
        try {
            long startedAt = System.currentTimeMillis();
            ToolRegistry.Executed result = registry.execute(
                    new ToolRegistry.PendingCall("c1", "Agent", call("do work")));
            long elapsed = System.currentTimeMillis() - startedAt;

            Assertions.assertTrue(result.error(), "超时必须以失败返回");
            Assertions.assertTrue(result.content().contains("time budget"),
                    "超时文案应说明是委派预算耗尽: " + result.content());
            Assertions.assertTrue(elapsed < 5_000, "应在预算附近返回而不是挂满整段: " + elapsed);
            // 超时会 cancel(true)，委派线程应收到中断而不是继续空跑
            Assertions.assertTrue(launcher.interrupted.await(3, TimeUnit.SECONDS),
                    "超时后委派线程应被中断");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail(e);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void delegationFailureIsMarkedStructurallyInModelVisibleContent() {
        ToolRegistry registry = registry(new FailingLauncher(), 0, 0);
        try {
            ToolRegistry.Executed result = registry.execute(
                    new ToolRegistry.PendingCall("c2", "Agent", call("do work")));
            Assertions.assertFalse(result.error(), "子代理业务失败不升级为工具执行异常");
            Assertions.assertTrue(result.content().contains("<delegation_failed"),
                    "失败结果应带结构化标记: " + result.content());
            Assertions.assertTrue(result.content().contains("could not complete the task"),
                    "应给出可操作的后续动作: " + result.content());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void parallelDelegationsAreCappedByTurnQuota() {
        // 配额 2、单次 400ms：并发提交 4 个，应只有 2 个同时在跑，且其余在排队而非失败
        SlowLauncher launcher = new SlowLauncher(400);
        ToolRegistry registry = registry(launcher, 0, 2);
        try {
            java.util.List<java.util.concurrent.Future<ToolRegistry.Executed>> futures =
                    new java.util.ArrayList<>();
            java.util.concurrent.ExecutorService callers =
                    java.util.concurrent.Executors.newFixedThreadPool(4);
            for (int i = 0; i < 4; i++) {
                String callId = "p" + i;
                futures.add(callers.submit(() -> registry.execute(
                        new ToolRegistry.PendingCall(callId, "Agent", call("work")))));
            }
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
            callers.shutdownNow();
            Assertions.assertTrue(launcher.peak.get() <= 2,
                    "并发委派不得超过单轮配额，实际峰值 " + launcher.peak.get());
        } catch (Exception e) {
            Assertions.fail(e);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void delegationRejectedWhenNoPermitIsAvailable() {
        SlowLauncher launcher = new SlowLauncher(800);
        ToolRegistry registry = registry(launcher, 0, 1);
        java.util.concurrent.ExecutorService callers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            AtomicReference<String> firstContent = new AtomicReference<>();
            java.util.concurrent.Future<?> first = callers.submit(() ->
                    firstContent.set(registry.execute(
                            new ToolRegistry.PendingCall("q1", "Agent", call("hold"))).content()));
            // 等第一个委派持锁
            Thread.sleep(200);
            ToolRegistry.Executed second = registry.execute(
                    new ToolRegistry.PendingCall("q2", "Agent", call("second")));
            first.get(20, TimeUnit.SECONDS);

            Assertions.assertTrue(second.error(), "配额用尽应快速失败");
            Assertions.assertTrue(second.content().contains("already running in this turn"),
                    "应说明是本轮委派上限: " + second.content());
            // 正常结果会附带 agentId/usage 尾巴，断言开头即可
            Assertions.assertTrue(firstContent.get().startsWith("done"),
                    "首个委派应正常完成: " + firstContent.get());
        } catch (Exception e) {
            Assertions.fail(e);
        } finally {
            callers.shutdownNow();
            registry.shutdown();
        }
    }
}
