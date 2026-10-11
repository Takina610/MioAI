package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.task.BackgroundTasks;
import com.mio.ai.framework.zagent.task.DetachedBash;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.TASK_STOP;

/**
 * TaskStop 工具（zcode handlers/task-stop.ts 的对位移植）：
 * 终止后台任务——脱离式 bash 直接杀沙箱进程组（TERM→KILL）；
 * 进程内任务（子代理）打断其执行线程，协作式 stopRequested 标记并行置位。
 */
final class TaskStopTool {

    private TaskStopTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "task_id":{"type":"string","description":"The ID of the task to stop"}},
                "required":["task_id"]}""";
        return ToolEntry.ofMutable("TaskStop", TASK_STOP, schema, 30_000, TaskStopTool::execute);
    }

    static String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String taskId = Args.str(input, "task_id");
        if (taskId == null) {
            throw new ToolUseFailure(1, "Missing required parameter: task_id");
        }
        BackgroundTasks.Task task = ctx.tasks.get(taskId)
                .orElseThrow(() -> new ToolUseFailure(1, "No task found with ID: " + taskId));
        if (!"running".equals(task.status)) {
            throw new ToolUseFailure(3, "Task " + taskId + " is not running (status: " + task.status + ").");
        }
        task.stopRequested = true;
        if (task.detached && ctx.fs != null) {
            DetachedBash.kill(ctx.fs, task);
            ctx.tasks.completeFromProbe(task, "stopped", null,
                    task.output == null || task.output.isBlank()
                            ? "Stopped by user request (TaskStop)." : task.output);
            return "Stopped task " + taskId + " (" + task.type + ": " + task.description + ").";
        }
        Thread runner = task.runnerThread;
        if (runner != null) {
            runner.interrupt();
        }
        return "Stop requested for task " + taskId + " (" + task.type + ": " + task.description + ").";
    }
}
