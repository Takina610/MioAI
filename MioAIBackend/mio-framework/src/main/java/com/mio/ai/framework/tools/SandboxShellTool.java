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

    @Tool(description = "在 Linux 沙箱的 bash 中执行命令，stdout 与 stderr 合并返回，过长时保留末尾。"
            + "适合：运行/调试代码（python3 等）、数据处理、curl 调用公开 API、系统查询。"
            + "工作目录内容跨命令持久。默认超时 120 秒（最长 600）。"
            + "文件浏览用 ls/find，读文件建议用 readFile（带行号与分段）")
    public String runCommand(
            @ToolParam(description = "要执行的 bash 命令，可用 && 串联多步") String command,
            @ToolParam(description = "超时秒数（可选，默认 120，最大 600）", required = false) Integer timeoutSeconds) {
        return session.exec(command, timeoutSeconds).output();
    }
}
