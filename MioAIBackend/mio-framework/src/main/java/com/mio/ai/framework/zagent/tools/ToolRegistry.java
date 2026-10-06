package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具注册表（zcode ToolRegistry 的对位移植）：
 * name → ToolEntry；统一执行入口承载 JSON 解析、超时、失败信封
 * （&lt;tool_use_error&gt; 包裹、未知工具文案），循环永不因工具失败中断。
 */
@Slf4j
public final class ToolRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 一次执行的结果（含失败信封内容；media 为 Read 图片等媒体负载，纯文本结果为空） */
    public record Executed(String toolName, String content, boolean error, long durationMs,
                           List<ToolMedia> media) {

        public Executed(String toolName, String content, boolean error, long durationMs) {
            this(toolName, content, error, durationMs, List.of());
        }
    }

    /** 待执行的调用（模型步的输出） */
    public record PendingCall(String id, String name, String arguments) {
    }

    private final Map<String, ToolEntry> entries = new LinkedHashMap<>();
    private final ToolContext context;
    private final ExecutorService executor;

    public ToolRegistry(ToolContext context) {
        this.context = context;
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "zagent-tool");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void register(ToolEntry entry) {
        entries.put(entry.name, entry);
    }

    /** MCP 工具包装（zcode 把 MCP 工具并入同一注册表） */
    public void registerMcpCallbacks(List<ToolCallback> callbacks) {
        if (callbacks == null) {
            return;
        }
        for (ToolCallback callback : callbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            if (definition == null || entries.containsKey(definition.name())) {
                continue;
            }
            register(ToolEntry.ofMutable(definition.name(), definition.description(),
                    definition.inputSchema(), 300_000,
                    (input, ctx) -> callback.call(input.toString())));
        }
    }

    public List<ToolEntry> entries() {
        return new ArrayList<>(entries.values());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 供提供方请求携带工具定义（仅发 schema；执行永远走本注册表） */
    public List<ToolCallback> asToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolEntry entry : entries.values()) {
            ToolDefinition definition = new DefaultToolDefinition(
                    entry.name, entry.description, entry.inputSchema);
            callbacks.add(new ToolCallback() {
                @Override
                public ToolDefinition getToolDefinition() {
                    return definition;
                }

                @Override
                public String call(String toolInput) {
                    return executeByName(entry.name, toolInput);
                }
            });
        }
        return callbacks;
    }

    public String executeByName(String name, String argumentsJson) {
        Executed result = execute(new PendingCall("sync", name, argumentsJson));
        return result.content();
    }

    /** 统一执行入口：解析、超时、失败信封（zcode executor/errors.ts 语义） */
    public Executed execute(PendingCall call) {
        long startedAt = System.currentTimeMillis();
        ToolEntry entry = entries.get(call.name());
        if (entry == null) {
            return new Executed(call.name(),
                    "<tool_use_error>Error: No such tool available: " + call.name() + "</tool_use_error>",
                    true, 0);
        }
        JsonNode input;
        try {
            input = MAPPER.readTree(call.arguments() == null || call.arguments().isBlank()
                    ? "{}" : call.arguments());
        } catch (Exception e) {
            return new Executed(call.name(),
                    "<tool_use_error>Invalid JSON in tool arguments: " + e.getMessage() + "</tool_use_error>",
                    true, System.currentTimeMillis() - startedAt);
        }
        try {
            Future<ToolResult> future = executor.submit(() -> entry.handler().execute(input, context));
            ToolResult result;
            try {
                result = entry.timeoutMs() > 0
                        ? future.get(entry.timeoutMs(), TimeUnit.MILLISECONDS)
                        : future.get();
            } catch (TimeoutException timeout) {
                future.cancel(true);
                return new Executed(call.name(),
                        "<tool_use_error>Tool execution exceeded timeout of " + entry.timeoutMs()
                                + "ms</tool_use_error>",
                        true, System.currentTimeMillis() - startedAt);
            } catch (java.util.concurrent.ExecutionException execution) {
                Throwable cause = execution.getCause() == null ? execution : execution.getCause();
                if (cause instanceof ToolUseFailure failure) {
                    return new Executed(call.name(), failure.toModelContent(), true,
                            System.currentTimeMillis() - startedAt);
                }
                log.warn("工具执行异常 {}: {}", call.name(), cause.getMessage());
                return new Executed(call.name(),
                        "<tool_use_error>" + call.name() + " failed: " + cause.getMessage() + "</tool_use_error>",
                        true, System.currentTimeMillis() - startedAt);
            }
            String content = result.content() == null ? "" : result.content();
            return new Executed(call.name(), content, false,
                    System.currentTimeMillis() - startedAt, result.media());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Executed(call.name(), "<tool_use_error>Tool execution was interrupted</tool_use_error>",
                    true, System.currentTimeMillis() - startedAt);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
