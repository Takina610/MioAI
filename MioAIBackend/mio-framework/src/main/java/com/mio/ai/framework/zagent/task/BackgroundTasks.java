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

        Task(String id, String type, String description, String chatId) {
            this.id = id;
            this.type = type;
            this.description = description;
            this.chatId = chatId;
        }
    }

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<String>> notifications = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public Task register(String chatId, String type, String description) {
        String id = "b_" + seq.incrementAndGet();
        Task task = new Task(id, type, description, chatId);
        tasks.put(id, task);
        return task;
    }

    public Optional<Task> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(tasks.get(id));
    }

    public void complete(Task task, boolean failed, Integer exitCode, String output) {
        task.status = failed ? "failed" : "completed";
        task.exitCode = exitCode;
        task.output = output == null ? "" : output;
        task.finishedAt = System.currentTimeMillis();
        notify(task);
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
}
