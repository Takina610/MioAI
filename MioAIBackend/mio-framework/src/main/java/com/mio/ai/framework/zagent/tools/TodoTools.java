package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.framework.zagent.history.TodoItem;

import java.util.ArrayList;
import java.util.List;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.TODO_READ;
import static com.mio.ai.framework.zagent.tools.ToolDescriptions.TODO_WRITE;

/**
 * TodoRead/TodoWrite 工具（zcode handlers/todo.ts 的对位移植）：
 * 整表替换、状态/优先级枚举校验；清单变化经引擎以 plan 事件推给前端。
 */
final class TodoTools {

    private static final int MAX_TODOS = 100;

    private TodoTools() {
    }

    static ToolEntry readEntry() {
        return ToolEntry.ofReadOnly("TodoRead", TODO_READ,
                "{\"type\":\"object\",\"properties\":{},\"required\":[]}", TodoTools::executeRead);
    }

    static ToolEntry writeEntry() {
        String schema = """
                {"type":"object","properties":{
                "todos":{"type":"array","items":{"type":"object","properties":{
                "content":{"type":"string","minLength":1,"description":"Brief description of the task"},
                "status":{"type":"string","enum":["pending","in_progress","completed"],"description":"Current status of the task"},
                "priority":{"type":"string","enum":["high","medium","low"],"description":"Priority level of the task"}},
                "required":["content","status","priority"]},
                "description":"The complete updated todo list. At most one item may be in_progress at a time."}}},
                "required":["todos"]}""";
        return ToolEntry.ofMutable("TodoWrite", TODO_WRITE, schema, 10_000, TodoTools::executeWrite);
    }

    static String executeRead(JsonNode input, ToolContext ctx) {
        return renderTodos(ctx.state.todos());
    }

    static String executeWrite(JsonNode input, ToolContext ctx) {
        if (input == null || !input.has("todos") || !input.get("todos").isArray()) {
            throw new ToolUseFailure(1, "todos must be an array.");
        }
        List<TodoItem> parsed = new ArrayList<>();
        int index = 0;
        for (JsonNode item : input.get("todos")) {
            if (++index > MAX_TODOS) {
                break;
            }
            String content = Args.str(item, "content");
            String status = Args.str(item, "status");
            String priority = Args.str(item, "priority");
            if (content == null) {
                throw new ToolUseFailure(2, "todos[" + (index - 1) + "].content must not be empty.");
            }
            if (status == null || !List.of("pending", "in_progress", "completed").contains(status)) {
                throw new ToolUseFailure(3, "todos[" + (index - 1) + "].status must be one of: "
                        + "pending, in_progress, completed.");
            }
            if (priority == null || !List.of("high", "medium", "low").contains(priority)) {
                throw new ToolUseFailure(4, "todos[" + (index - 1) + "].priority must be one of: "
                        + "high, medium, low.");
            }
            parsed.add(new TodoItem(content, status, priority));
        }
        ctx.state.setTodos(parsed);
        return renderTodos(parsed);
    }

    private static String renderTodos(List<TodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            return "No todos";
        }
        StringBuilder sb = new StringBuilder();
        for (TodoItem todo : todos) {
            String marker = switch (todo.status()) {
                case "completed" -> "x";
                case "in_progress" -> ">";
                default -> " ";
            };
            sb.append("[").append(marker).append("] ").append(todo.content())
                    .append(" (").append(todo.priority()).append(")\n");
        }
        return sb.toString();
    }
}
