package com.mio.ai.framework.zagent.task;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 后台任务收尾回归：任务登记后必须有一次终态。
 * <p>有界池引入了一个新的失败面——提交被拒时任务已进入注册表。若不就地收尾，
 * 会留下永远 "running" 的幽灵任务：TaskOutput 永远查不到结果、TaskStop 也无从下手，
 * 且启动对账还会把它当作被中断的正常任务再处理一遍。
 */
class BackgroundTaskTerminalStateTest {

    private static BackgroundTasks freshTasks() {
        // 单例足够：清理只影响本次断言用的 id
        return BackgroundTasks.instance();
    }

    @Test
    void rejectedSubmissionCanBeCompletedAsFailed() {
        BackgroundTasks tasks = freshTasks();
        BackgroundTasks.Task task = tasks.register("chat-ghost", "agent", "ghost task");
        Assertions.assertEquals("running", task.status);

        // 模拟池满被拒后的收尾路径
        tasks.complete(task, true, null, "(Delegation rejected: the background agent pool is full.)");

        Assertions.assertEquals("failed", task.status);
        Assertions.assertTrue(task.output.contains("pool is full"));
        Assertions.assertTrue(task.finishedAt > 0, "终态必须带结束时间");
    }

    @Test
    void completingTwiceIsIdempotent() {
        // 看门狗与正常返回存在竞态：后到的收尾绝不能覆盖已有终态与结果
        BackgroundTasks tasks = freshTasks();
        BackgroundTasks.Task task = tasks.register("chat-idem", "agent", "idem task");
        tasks.complete(task, false, null, "real result");
        tasks.complete(task, true, null, "late watchdog");

        Assertions.assertEquals("completed", task.status);
        Assertions.assertEquals("real result", task.output);
    }

    @Test
    void completedTaskIsNotEvictedWhileRunningPeersStay() {
        // 淘汰只清已完成任务：并发运行中的任务不能被误删
        BackgroundTasks tasks = freshTasks();
        BackgroundTasks.Task running = tasks.register("chat-keep", "agent", "still running");
        for (int i = 0; i < 260; i++) {
            BackgroundTasks.Task done = tasks.register("chat-evict", "agent", "done " + i);
            tasks.complete(done, false, 0, "ok");
        }
        Assertions.assertEquals("running", running.status);
        Assertions.assertTrue(tasks.get(running.id).isPresent(), "运行中的任务不能被淘汰");
    }

    @Test
    void finishedTaskKeepsOutputForLaterTaskOutput() {
        BackgroundTasks tasks = freshTasks();
        BackgroundTasks.Task task = tasks.register("chat-output", "agent", "output task");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            big.append("line ").append(i).append('\n');
        }
        tasks.complete(task, false, null, big.toString());

        Assertions.assertEquals("completed", task.status);
        Assertions.assertTrue(task.output.startsWith("line 0"));
        Assertions.assertTrue(task.output.contains("line 499"));
    }
}
