package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.task.BackgroundTasks;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.TASK_OUTPUT;

/**
 * TaskOutput 工具（zcode handlers/task-output.ts 的对位移植）：
 * 阻塞/非阻塞查询后台任务，输出超长保尾部并注明全文位置。
 */
final class TaskOutputTool {

    private static final int MAX_OUTPUT_LENGTH = 32_000;

    private TaskOutputTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "task_id":{"type":"string","description":"The task ID to get output from"},
                "block":{"type":"boolean","description":"Whether to wait for completion"},
                "timeout":{"type":"number","description":"Max wait time in ms"}},
                "required":["task_id","block","timeout"]}""";
        return ToolEntry.ofMutable("TaskOutput", TASK_OUTPUT, schema, 620_000, TaskOutputTool::execute);
    }

    static String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String taskId = Args.str(input, "task_id");
        if (taskId == null) {
            throw new ToolUseFailure(1, "Task ID is required.");
        }
        boolean block = input == null || !input.hasNonNull("block") || Args.bool(input, "block");
        Integer timeoutMs = Args.intVal(input, "timeout");
        long timeout = timeoutMs != null && timeoutMs >= 0
                ? Math.min(timeoutMs, 600_000) : 30_000;

        BackgroundTasks.Task task = ctx.tasks.get(taskId)
                .orElseThrow(() -> new ToolUseFailure(2, "No task found with ID: " + taskId));

        if (block && "running".equals(task.status)) {
            long deadline = System.currentTimeMillis() + timeout;
            while ("running".equals(task.status) && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<retrieval_status>").append("running".equals(task.status) ? "not_ready" : "success")
                .append("</retrieval_status>\n");
        sb.append("<task_id>").append(task.id).append("</task_id>\n");
        sb.append("<task_type>").append(task.type).append("</task_type>\n");
        sb.append("<status>").append(task.status).append("</status>\n");
        if (task.exitCode != null) {
            sb.append("<exit_code>").append(task.exitCode).append("</exit_code>\n");
        }
        String output = task.output == null ? "" : task.output;
        if (output.length() > MAX_OUTPUT_LENGTH) {
            output = "[Truncated: output exceeds " + MAX_OUTPUT_LENGTH + " chars]\n"
                    + output.substring(output.length() - MAX_OUTPUT_LENGTH);
        }
        sb.append("<output>\n").append(output).append("\n</output>");
        return sb.toString();
    }
}
