package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.task.BackgroundTasks;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.TASK_STOP;

/**
 * TaskStop 工具（zcode handlers/task-stop.ts 的对位移植）：
 * 请求终止后台任务（协作式：任务侧在完成检查点响应 stopRequested）。
 */
final class TaskStopTool {

    private TaskStopTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "task_id":{"type":"string","description":"The ID of the task to stop"}},
                "required":["task_id"]}""";
        return ToolEntry.ofMutable("TaskStop", TASK_STOP, schema, 10_000, TaskStopTool::execute);
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
        return "Stop requested for task " + taskId + " (" + task.type + ": " + task.description + ").";
    }
}
