package com.mio.ai.framework.zagent.task;

import java.util.List;
import java.util.Optional;

/**
 * 后台任务持久化仓储（zcode 无此层——MioAI 补齐"进程重启任务丢失"缺口）：
 * 引擎在登记/完结时回调 upsert；TaskOutput 内存未命中经 find 重连。
 * 实现方在 mio-agent（MySQL），引擎侧只依赖本接口。
 */
public interface BackgroundTaskStore {

    void upsert(BackgroundTasks.Task task);

    Optional<BackgroundTasks.Task> find(String taskId);

    List<BackgroundTasks.Task> findByChatAndStatus(String chatId, String status);

    List<BackgroundTasks.Task> findByStatus(String status);

    /** 库内最大任务序号（b_&lt;n&gt; 的 n）：重启后续接注册序号，避免 ID 撞车 */
    long maxTaskSeq();
}
