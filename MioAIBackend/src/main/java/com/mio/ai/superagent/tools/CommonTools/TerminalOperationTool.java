package com.mio.ai.superagent.tools.CommonTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
/**
 * @author: Takina
 * @date: 2026/3/31 18:47
 * @description: 终端操作工具（带命令白名单，防止执行任意命令）
 */

@Component
public class TerminalOperationTool {

    private final TerminalCommandPolicy policy;

    public TerminalOperationTool(
            @Value("${mio.ai.tools.terminal.allowed-commands:}") String allowedCommands) {
        this.policy = (allowedCommands == null || allowedCommands.isBlank())
                ? TerminalCommandPolicy.defaultPolicy()
                : TerminalCommandPolicy.fromConfig(allowedCommands);
    }

    @Tool(description = "在终端中执行命令。出于安全考虑仅允许白名单内的单条命令"
            + "（如 dir、type、java、python、pip、npm、git 等只读或开发类命令），"
            + "不支持管道、链式执行和输出重定向")
    public String executeTerminalCommand(@ToolParam(description = "要在终端中执行的命令") String command) {
        String rejection = policy.checkAllowed(command);
        if (rejection != null) {
            return "命令被安全策略拒绝：" + rejection;
        }
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("命令执行失败，退出码: ").append(exitCode);
            }
        } catch (IOException | InterruptedException e) {
            output.append("执行命令错误: ").append(e.getMessage());
        }
        return output.toString();
    }
}
