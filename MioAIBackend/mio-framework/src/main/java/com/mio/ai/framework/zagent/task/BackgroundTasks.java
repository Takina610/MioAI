package com.mio.ai.framework.zagent.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 后台任务注册表（zcode runtime-task registry 的对位移植）：
 * run_in_background 的 bash 命令与子代理在此登记，TaskOutput/TaskStop 查询与终止；
 * 完成后向所属会话排队 &lt;task-notification&gt;，下一轮请求注入。
 * <p>持久化：任务登记/完结即写入 {@link BackgroundTaskStore}（后端重启后 bash 任务
 * 仍活 在沙箱——TaskOutput 经 {@link DetachedBash} 探针重连收尾，非沙箱任务标记
 * interrupted；注册序号自库内最大值续接避免 ID 撞车）。
 */
public final class BackgroundTasks {

    private static final BackgroundTasks INSTANCE = new BackgroundTasks();

    public static BackgroundTasks instance() {
        return INSTANCE;
    }

    /** 后台任务快照 */
    public static final class Task {
        public final String id;
        public final String type;
        public final String description;
        public volatile String status = "running";
        public volatile Integer exitCode;
        public volatile String output = "";
        public final long startedAt = System.currentTimeMillis();
        public volatile long finishedAt;
        public volatile boolean stopRequested;
        public final String chatId;

        /** 沙箱脱离式任务（run_in_background bash）：进程活在沙箱，跨后端重启可重连 */
        public volatile boolean detached;
        /** 沙箱侧进程 PID（setsid 会话首进程，kill -- -pid 可灭整组） */
        public volatile Long sandboxPid;
        /** 超时截止（epoch ms，0 = 不限时） */
        public volatile long deadlineAt;
        /** 进程内任务（子代理）的执行线程：TaskStop 据此打断 */
        public volatile Thread runnerThread;

        Task(String id, String type, String description, String chatId) {
            this.id = id;
            this.type = type;
            this.description = description;
            this.chatId = chatId;
        }

        /** 从持久化快照重建（进程重启后的重连入口） */
        public static Task rehydrated(String id, String type, String description, String chatId,
                                      String status, Integer exitCode, String output,
                                      long startedAt, long finishedAt, boolean detached,
                                      Long sandboxPid, long deadlineAt) {
            Task task = new Task(id, type, description, chatId);
            if (!"running".equals(status)) {
                task.status = status;
                task.exitCode = exitCode;
                task.output = output == null ? "" : output;
                task.finishedAt = finishedAt;
            }
            task.detached = detached;
            task.sandboxPid = sandboxPid;
            task.deadlineAt = deadlineAt;
            return task;
        }
    }

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> notifications = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    /** 已完成任务保留上限：TaskOutput 需要事后读取输出，但不能无限累积（静态 map 只增不删） */
    private static final int MAX_FINISHED_TASKS = 200;

    /** 持久化仓储（启动时由 mio-agent 装配；缺省时任务仅存内存） */
    private static volatile BackgroundTaskStore store;

    public static void setStore(BackgroundTaskStore taskStore) {
        store = taskStore;
    }

    public Task register(String chatId, String type, String description) {
        evictFinished();
        String id = "b_" + seq.incrementAndGet();
        Task task = new Task(id, type, description, chatId);
        tasks.put(id, task);
        persist(task);
        return task;
    }

    /** 已完成任务超过上限时清掉最旧的一批（运行中任务不受影响） */
    private void evictFinished() {
        if (tasks.size() <= MAX_FINISHED_TASKS) {
            return;
        }
        tasks.values().stream()
                .filter(task -> !"running".equals(task.status))
                .sorted(java.util.Comparator.comparingLong(task -> task.finishedAt))
                .limit(tasks.size() - MAX_FINISHED_TASKS)
                .forEach(task -> tasks.remove(task.id));
    }

    public Optional<Task> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Task task = tasks.get(id);
        if (task != null) {
            return Optional.of(task);
        }
        return rehydrate(id);
    }

    /** 内存未命中回库重建：完结任务只读；存活 bash 任务转探针收尾；进程内任务标记中断 */
    private Optional<Task> rehydrate(String id) {
        BackgroundTaskStore taskStore = store;
        if (taskStore == null) {
            return Optional.empty();
        }
        Optional<Task> restored = taskStore.find(id);
        if (restored.isEmpty()) {
            return Optional.empty();
        }
        Task task = restored.get();
        if ("running".equals(task.status) && !task.detached) {
            // 后端重启杀死了进程内执行线程（子代理），沙箱外无此任务
            task.status = "interrupted";
            task.output = task.output == null || task.output.isBlank()
                    ? "Backend restarted while this task was running; execution was lost." : task.output;
            task.finishedAt = System.currentTimeMillis();
            persist(task);
        }
        tasks.put(id, task);
        return Optional.of(task);
    }

    public void complete(Task task, boolean failed, Integer exitCode, String output) {
        synchronized (task) {
            if (!"running".equals(task.status)) {
                return;
            }
            task.status = failed ? "failed" : "completed";
            task.exitCode = exitCode;
            task.output = output == null ? "" : output;
            task.finishedAt = System.currentTimeMillis();
            persist(task);
        }
        notify(task);
    }

    /** 探针收尾（脱离式 bash）：以沙箱侧事实为准回写状态并入队通知；已终态的任务幂等跳过 */
    public void completeFromProbe(Task task, String status, Integer exitCode, String output) {
        synchronized (task) {
            if (!"running".equals(task.status)) {
                return;
            }
            task.status = status;
            task.exitCode = exitCode;
            task.output = output == null ? "" : output;
            task.finishedAt = System.currentTimeMillis();
            persist(task);
        }
        notify(task);
    }

    /**
     * 登记后的字段回写（脱离启动成功后的 detached/pid/deadline）：
     * 不回写的话重启对账会把仍活着的沙箱任务误判为进程内任务而改判 interrupted。
     */
    public void touch(Task task) {
        persist(task);
    }

    /** 启动期对账：进程内任务的运行态必然已死（重启），统一改判 interrupted；注册序号续接 */
    public void reconcileOnStartup() {
        BackgroundTaskStore taskStore = store;
        if (taskStore == null) {
            return;
        }
        try {
            seq.set(Math.max(seq.get(), taskStore.maxTaskSeq()));
            for (Task task : taskStore.findByStatus("running")) {
                if (!task.detached) {
                    task.status = "interrupted";
                    task.output = task.output == null || task.output.isBlank()
                            ? "Backend restarted while this task was running; execution was lost."
                            : task.output;
                    task.finishedAt = System.currentTimeMillis();
                    persist(task);
                }
            }
        } catch (Exception e) {
            // 对账失败不阻塞启动
        }
    }

    /**
     * 轮次开始前刷新本会话的脱离式任务：探针查沙箱侧事实，完结者补状态并入队通知
     * （后台任务在后端重启后仍能产生 task-notification）。
     *
     * @param fs 沙箱文件系统；null（沙箱未启用）时跳过
     */
    public void refreshDetached(String chatId, com.mio.ai.framework.zagent.tools.SandboxFs fs) {
        BackgroundTaskStore taskStore = store;
        if (fs == null || taskStore == null) {
            return;
        }
        List<Task> candidates = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Task task : tasks.values()) {
            if ("running".equals(task.status) && task.detached && chatId.equals(task.chatId)) {
                candidates.add(task);
                seen.add(task.id);
            }
        }
        // 重启后内存为空：从库里捞该会话仍标记 running 的脱离任务
        for (Task task : taskStore.findByChatAndStatus(chatId, "running")) {
            if (task.detached && !seen.contains(task.id)) {
                candidates.add(task);
                tasks.put(task.id, task);
            }
        }
        for (Task task : candidates) {
            DetachedBash.ProbeResult probe = DetachedBash.probe(fs, task, 0);
            if (probe == null) {
                continue;
            }
            refreshFromProbe(fs, task, probe);
        }
    }

    /** 探针结果落地（TaskOutput 阻塞轮询同样走此入口）：done → completed/failed；gone → failed；超时先杀再改判 failed */
    public void refreshFromProbe(com.mio.ai.framework.zagent.tools.SandboxFs fs, Task task,
                                 DetachedBash.ProbeResult probe) {
        long deadline = task.deadlineAt;
        if ("running".equals(probe.status()) && deadline > 0 && System.currentTimeMillis() > deadline) {
            DetachedBash.kill(fs, task);
            completeFromProbe(task, "failed", null,
                    (probe.outputTail() == null ? "" : probe.outputTail() + "\n")
                            + "<error>Background command timed out and was aborted</error>");
            return;
        }
        switch (probe.status()) {
            case "done" -> completeFromProbe(task,
                    probe.exitCode() != null && probe.exitCode() != 0 ? "failed" : "completed",
                    probe.exitCode(), probe.outputTail());
            case "gone" -> completeFromProbe(task, "failed", null,
                    (probe.outputTail() == null || probe.outputTail().isBlank()
                            ? "Process exited without recording an exit code (killed or crashed)."
                            : probe.outputTail()));
            default -> {
                // 仍在运行：保持 running，等 TaskOutput 轮询或下一轮刷新
            }
        }
    }

    /** zcode 任务完成通知（下一轮以 system-reminder 注入） */
    private void notify(Task task) {
        String exit = task.exitCode != null ? " (exit code " + task.exitCode + ")" : "";
        String message = "<task-notification>\nBackground task " + task.id + " (" + task.type
                + ": " + task.description + ") finished with status " + task.status + exit
                + ".\nUse TaskOutput with task_id \"" + task.id + "\" to read its full output"
                + " before responding to the user.\n</task-notification>";
        notifications.computeIfAbsent(task.chatId, key -> new ArrayList<>()).add(message);
    }

    /** 取走该会话积压的任务通知（注入后即清除） */
    public List<String> drainNotifications(String chatId) {
        List<String> pending = notifications.remove(chatId);
        return pending == null ? List.of() : pending;
    }

    private static void persist(Task task) {
        BackgroundTaskStore taskStore = store;
        if (taskStore == null) {
            return;
        }
        try {
            taskStore.upsert(task);
        } catch (Exception ignored) {
            // 持久化失败不阻断任务流
        }
    }
}
