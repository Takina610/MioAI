package com.mio.ai.framework.zagent.tools;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.zagent.task.BackgroundTasks;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.BASH;
import static com.mio.ai.framework.zagent.tools.ToolDescriptions.BASH_COMMAND_PARAM;

/**
 * Bash 工具（zcode handlers/bash.ts 的对位移植）：
 * 工作目录跨调用持久、默认 120s/上限 600s 超时、失败首行 "Exit code N"、
 * run_in_background 后台执行（TaskOutput 查询 + 下轮任务通知）、超大输出截断信封。
 */
final class BashTool {

    private static final int DEFAULT_TIMEOUT_MS = 120_000;
    private static final int MAX_TIMEOUT_MS = 600_000;
    private static final int MAX_OUTPUT_CHARS = 30_000;

    /** 后台命令执行线程池（守护线程，量小常驻） */
    private static final ExecutorService BACKGROUND_POOL = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "zagent-bash-bg");
        thread.setDaemon(true);
        return thread;
    });

    /** 会话级持久 cwd（相对工作目录）；离开工作目录时重置（zcode 语义） */
    private final ThreadLocal<String> cwd = new ThreadLocal<>();

    BashTool() {
    }

    ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "command":{"type":"string","description":"The command to execute"},
                "timeout":{"type":"number","description":"Optional timeout in milliseconds (max 600000)"},
                "description":{"type":"string","description":%s},
                "run_in_background":{"type":"boolean","description":"Set to true to run this command in the background."}},
                "required":["command"]}""".formatted(jsonQuote(BASH_COMMAND_PARAM));
        return ToolEntry.ofMutable("Bash", BASH, schema, MAX_TIMEOUT_MS + 10_000, this::execute);
    }

    String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String command = Args.str(input, "command");
        if (command == null || command.isBlank()) {
            return "";
        }
        Integer timeoutMs = Args.intVal(input, "timeout");
        String description = StrUtil.blankToDefault(Args.str(input, "description"), command);
        int effectiveTimeout = timeoutMs != null && timeoutMs > 0
                ? Math.min(timeoutMs, MAX_TIMEOUT_MS) : DEFAULT_TIMEOUT_MS;
        boolean background = Args.bool(input, "run_in_background");

        if (background) {
            return runBackground(ctx, command, effectiveTimeout, description);
        }
        return runForeground(ctx, command, effectiveTimeout);
    }

    private String runForeground(ToolContext ctx, String command, int timeoutMs) {
        String wrapped = wrap(command);
        SandboxFs.ExecResult result = ctx.fs.exec(wrapped, timeoutMs / 1000, cwd.get());
        updateCwd(ctx, result.output());
        String output = stripCwdMarker(result.output());
        return render(output, result.exitCode(), result.timedOut());
    }

    private String runBackground(ToolContext ctx, String command, int timeoutMs, String description) {
        BackgroundTasks.Task task = ctx.tasks.register(ctx.chatId, "bash", description);
        BACKGROUND_POOL.execute(() -> {
            String wrapped = wrap(command);
            SandboxFs.ExecResult result = ctx.fs.exec(wrapped, timeoutMs / 1000, null);
            ctx.tasks.complete(task, result.exitCode() != null && result.exitCode() != 0,
                    result.exitCode(), render(stripCwdMarker(result.output()), result.exitCode(), result.timedOut()));
        });
        return "Command running in background with ID: " + task.id
                + ". You will be notified when it completes. To check interim output, "
                + "use TaskOutput with task_id \"" + task.id + "\".";
    }

    /** 命令包装：失败不中断 pwd 采集（__MIO_CWD__ 标记行） */
    private String wrap(String command) {
        return "(" + command + ")\n__mio_rc=$?\nprintf '__MIO_CWD__%s\\n' \"$(pwd)\"\nexit $__mio_rc";
    }

    private void updateCwd(ToolContext ctx, String output) {
        List<String> lines = output.lines().toList();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.startsWith("__MIO_CWD__")) {
                String abs = line.substring("__MIO_CWD__".length());
                String rel = ctx.fs.stripRoot(abs);
                cwd.set(rel.equals(abs) ? null : rel);
                return;
            }
        }
    }

    private String stripCwdMarker(String output) {
        return output.replaceAll("(?m)^__MIO_CWD__.*\\n?", "");
    }

    /** zcode Bash 结果渲染：失败首行 Exit code N；超时附中止说明；超大输出截断信封 */
    static String render(String output, Integer exitCode, boolean timedOut) {
        String body = output == null ? "" : output;
        body = stripLeadingBlankLines(body).stripTrailing();
        boolean failed = exitCode != null && exitCode != 0;
        StringBuilder sb = new StringBuilder();
        if (failed) {
            sb.append("Exit code ").append(exitCode).append('\n');
        }
        if (body.length() > MAX_OUTPUT_CHARS) {
            sb.append("<persisted-output>\n")
                    .append("Output too large (").append(body.length()).append(" chars). ")
                    .append("Showing the beginning and the tail:\n\n")
                    .append(body, 0, 2000)
                    .append("\n\n[...output truncated...]\n\n")
                    .append(body.substring(body.length() - (MAX_OUTPUT_CHARS - 4000)))
                    .append("\n</persisted-output>");
        } else {
            sb.append(body);
        }
        if (timedOut) {
            sb.append("\n<error>Command was aborted before completion</error>");
        }
        return sb.toString();
    }

    private static String stripLeadingBlankLines(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == '\n' || text.charAt(i) == '\r')) {
            i++;
        }
        return text.substring(i);
    }

    private static String jsonQuote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
