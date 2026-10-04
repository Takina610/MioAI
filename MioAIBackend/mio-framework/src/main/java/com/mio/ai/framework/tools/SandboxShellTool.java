package com.mio.ai.framework.tools;

import com.mio.ai.framework.sandbox.SandboxSession;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 通用 shell 原语（参考 pi 的 bash / codex 的 unified_exec）：
 * 在沙箱这台真实 Linux 机器上执行任意命令——安装依赖、跑代码、处理数据、
 * 调用 curl 访问任何公开 API，都由模型自行组合完成，不需要领域专用工具。
 */
@Component
public class SandboxShellTool {

    private final SandboxSession session;

    public SandboxShellTool(SandboxSession session) {
        this.session = session;
    }

    @Tool(description = "Executes a bash command on your sandbox Linux machine and returns its output "
            + "(stdout and stderr merged). Each call starts in the working directory; "
            + "shell state (env vars, functions) does not persist between calls, but files do. "
            + "Good for: running/debugging code (python3), data processing, curl against public APIs, system inspection. "
            + "IMPORTANT: Avoid using this tool to run `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` "
            + "unless you have verified that a dedicated tool (readFile / grep / glob) cannot accomplish your task — "
            + "the dedicated tools give a much better experience. "
            + "Output longer than the cap keeps only the tail; re-run a more precise command for the full content. "
            + "timeoutSeconds: default 120, max 600. No sudo; interactive commands are not supported.")
    public String runCommand(
            @ToolParam(description = "要执行的 bash 命令，可用 && 串联多步") String command,
            @ToolParam(description = "超时秒数（可选，默认 120，最大 600）", required = false) Integer timeoutSeconds) {
        return session.exec(command, timeoutSeconds).output();
    }
}
