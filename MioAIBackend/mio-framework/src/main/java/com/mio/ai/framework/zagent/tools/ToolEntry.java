package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具条目（zcode ToolEntry 的对位移植）：提供方可见的定义元数据 + 执行器。
 * <p>readOnly/concurrentSafe/destructive 驱动调度分组；timeoutMs 为执行上限。
 */
public final class ToolEntry {

    /** 执行器：输入为已解析的参数 JSON，返回给模型看的结果（文本 + 可选媒体） */
    @FunctionalInterface
    public interface Handler {
        ToolResult execute(JsonNode input, ToolContext context) throws Exception;
    }

    /** 纯文本执行器：绝大多数工具只返回文本，工厂自动包装 */
    @FunctionalInterface
    public interface StringHandler {
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

    public static ToolEntry of(String name, String description, String inputSchema, StringHandler handler) {
        return new ToolEntry(name, description, inputSchema, true, true, false, 300_000, wrap(handler));
    }

    /** 需要门禁/串行的工具（写文件、跑命令、联网取数之外的副作用类） */
    public static ToolEntry ofMutable(String name, String description, String inputSchema,
                                      long timeoutMs, StringHandler handler) {
        return new ToolEntry(name, description, inputSchema, false, false, false, timeoutMs, wrap(handler));
    }

    /** 只读但联网（可并行，无需审批） */
    public static ToolEntry ofReadOnly(String name, String description, String inputSchema, StringHandler handler) {
        return new ToolEntry(name, description, inputSchema, true, true, false, 60_000, wrap(handler));
    }

    /** 可携带媒体结果的执行器变体（Read 图片分支） */
    public static ToolEntry ofReadOnly(String name, String description, String inputSchema, Handler handler) {
        return new ToolEntry(name, description, inputSchema, true, true, false, 60_000, handler);
    }

    private static Handler wrap(StringHandler handler) {
        return (input, context) -> ToolResult.of(handler.execute(input, context));
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
