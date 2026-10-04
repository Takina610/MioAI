package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具条目（zcode ToolEntry 的对位移植）：提供方可见的定义元数据 + 执行器。
 * <p>readOnly/concurrentSafe/destructive 驱动调度分组；timeoutMs 为执行上限。
 */
public final class ToolEntry {

    /** 执行器：输入为已解析的参数 JSON，返回给模型看的结果文本 */
    @FunctionalInterface
    public interface Handler {
        String execute(JsonNode input, ToolContext context) throws Exception;
    }

    public final String name;
    public final String description;
    public final String inputSchema;
    public final boolean readOnly;
    public final boolean concurrentSafe;
    public final boolean destructive;
    public final long timeoutMs;
    public final Handler handler;

    private ToolEntry(String name, String description, String inputSchema, boolean readOnly,
                      boolean concurrentSafe, boolean destructive, long timeoutMs, Handler handler) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.readOnly = readOnly;
        this.concurrentSafe = concurrentSafe;
        this.destructive = destructive;
        this.timeoutMs = timeoutMs;
        this.handler = handler;
    }

    public static ToolEntry of(String name, String description, String inputSchema, Handler handler) {
        return new ToolEntry(name, description, inputSchema, true, true, false, 300_000, handler);
    }

    /** 需要门禁/串行的工具（写文件、跑命令、联网取数之外的副作用类） */
    public static ToolEntry ofMutable(String name, String description, String inputSchema,
                                      long timeoutMs, Handler handler) {
        return new ToolEntry(name, description, inputSchema, false, false, false, timeoutMs, handler);
    }

    /** 只读但联网（可并行，无需审批） */
    public static ToolEntry ofReadOnly(String name, String description, String inputSchema, Handler handler) {
        return new ToolEntry(name, description, inputSchema, true, true, false, 60_000, handler);
    }

    public boolean parallelizable() {
        return readOnly && concurrentSafe && !destructive;
    }

    public Handler handler() {
        return handler;
    }

    public long timeoutMs() {
        return timeoutMs;
    }
}
