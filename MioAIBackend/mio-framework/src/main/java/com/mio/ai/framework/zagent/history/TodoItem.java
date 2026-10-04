package com.mio.ai.framework.zagent.history;

/**
 * 会话任务清单条目（zcode TodoWrite 的 todos 元素）。
 * <p>status: pending | in_progress | completed；priority: high | medium | low。
 */
public record TodoItem(String content, String status, String priority) {

    /** 前端计划面板的状态映射（旧协议沿用 not_started/in_progress/done） */
    public String displayStatus() {
        return switch (status == null ? "pending" : status) {
            case "in_progress" -> "in_progress";
            case "completed" -> "done";
            default -> "not_started";
        };
    }

    /** 从落库 plan 列表的展示状态反解（会话恢复时重建清单） */
    public static TodoItem fromDisplay(String content, String displayStatus) {
        String status = switch (displayStatus == null ? "" : displayStatus) {
            case "in_progress" -> "in_progress";
            case "done", "failed" -> "completed";
            default -> "pending";
        };
        return new TodoItem(content, status, "medium");
    }
}
