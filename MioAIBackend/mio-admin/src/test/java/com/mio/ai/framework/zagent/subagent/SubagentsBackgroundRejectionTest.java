package com.mio.ai.framework.zagent.subagent;

import com.mio.ai.framework.zagent.AgentEngineConfig;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.task.BackgroundTasks;
import com.mio.ai.framework.zagent.tools.contract.ToolsetFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * 后台委派被拒时的收尾回归（幽灵任务防护）。
 * <p>有界池有一个旧无界池不存在的失败面：提交被拒时任务**已经**登记进注册表。
 * 若不在拒绝分支就地收尾，任务会永久停留在 running——TaskOutput 查不到、
 * TaskStop 停不掉，还会被启动对账误当作"被中断的正常任务"。
 */
class SubagentsBackgroundRejectionTest {

    @AfterEach
    void restorePool() {
        Subagents.useBackgroundExecutor(null);
    }

    private static ExecutorService alwaysRejecting() {
        ExecutorService pool = Mockito.mock(ExecutorService.class);
        Mockito.when(pool.submit(Mockito.any(Runnable.class)))
                .thenThrow(new RejectedExecutionException("pool full"));
        Mockito.doThrow(new RejectedExecutionException("pool full"))
                .when(pool).execute(Mockito.any(Runnable.class));
        return pool;
    }

    private static Subagents subagents(BackgroundTasks tasks) {
        ToolsetFactory factory = Mockito.mock(ToolsetFactory.class);
        ToolsetFactory.RunContext runContext = new ToolsetFactory.RunContext(
                "chat-reject", null, List.of(), tasks);
        return new Subagents(Mockito.mock(ChatModel.class), AgentEngineConfig.defaults(), null,
                factory, runContext, Mockito.mock(AgentEvents.class), "test-model");
    }

    /** 立即执行但不做任何事的池：让 body 真正跑起来，用于验证异常兜底收尾 */
    private static ExecutorService inlineExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
        };
    }

    @Test
    void rejectedBackgroundDelegationIsClosedOutAsFailed() {
        Subagents.useBackgroundExecutor(alwaysRejecting());
        BackgroundTasks tasks = BackgroundTasks.instance();
        Subagents runner = subagents(tasks);
        // 会话级唯一描述：singleton 注册表里能精确定位本条
        String marker = "rejected-" + System.nanoTime();

        Assertions.assertThrows(RejectedExecutionException.class,
                () -> runner.launch("general-purpose", marker, "prompt", true));

        // 关键断言：不能留下 running 的幽灵任务
        BackgroundTasks.Task ghost = findTask(tasks, "chat-reject", marker);
        Assertions.assertNotNull(ghost, "任务应已登记");
        Assertions.assertEquals("failed", ghost.status,
                "池拒绝后任务必须收尾为失败，不能停在 running");
        Assertions.assertTrue(ghost.output.contains("pool is full"),
                "失败原因应可追溯: " + ghost.output);
    }

    @Test
    void backgroundDelegationThatThrowsIsStillClosedOut() {
        // 装配/执行期的 Error 绝不能逃逸到池线程外，否则任务永久 running。
        // 用 inline executor 同步跑 body，并以 mock 在注册表构造处抛 Error。
        Subagents.useBackgroundExecutor(inlineExecutor());
        BackgroundTasks tasks = BackgroundTasks.instance();
        ToolsetFactory factory = Mockito.mock(ToolsetFactory.class);
        // runEngine 走 7 参重载（hooks + ownerKey，最后一参为 null）；把该重载及
        // 更宽的 8 参/10 参一并 stub，避免因参数个数不匹配而静默不生效
        Mockito.when(factory.build(Mockito.anyString(), Mockito.any(), Mockito.anyList(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new AssertionError("boom from registry build"));
        Mockito.when(factory.build(Mockito.anyString(), Mockito.any(), Mockito.anyList(),
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new AssertionError("boom from registry build"));
        ToolsetFactory.RunContext runContext = new ToolsetFactory.RunContext(
                "chat-throw", null, List.of(), tasks);
        Subagents runner = new Subagents(Mockito.mock(ChatModel.class),
                // 关闭委派预算，确保走兜底 catch 而非预算中断分支
                new AgentEngineConfig(100, 1800, 3, 200_000, 0, 0),
                null, factory, runContext, Mockito.mock(AgentEvents.class), "test-model");
        String marker = "throw-" + System.nanoTime();

        // inline 执行器是同步的：若兜底缺失，Error 会直接抛回这里
        Assertions.assertDoesNotThrow(
                () -> runner.launch("general-purpose", marker, "prompt", true),
                "body 抛 Error 后不能逃逸到调用方");

        BackgroundTasks.Task task = findTask(tasks, "chat-throw", marker);
        Assertions.assertNotNull(task, "任务应已登记");
        Assertions.assertEquals("failed", task.status,
                "body 抛 Error 后任务必须收尾为失败，不能停在 running");
    }

    private static BackgroundTasks.Task findTask(BackgroundTasks tasks, String chatId, String description) {
        for (long i = 1; i < 100_000; i++) {
            var found = tasks.get("b_" + i);
            if (found.isEmpty()) {
                continue;
            }
            BackgroundTasks.Task task = found.get();
            if (chatId.equals(task.chatId) && description.equals(task.description)) {
                return task;
            }
        }
        return null;
    }
}
