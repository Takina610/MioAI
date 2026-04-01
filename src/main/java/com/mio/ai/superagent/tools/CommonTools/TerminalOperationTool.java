package com.mio.ai.superagent.tools.CommonTools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
/**
 * @author: Takina
 * @date: 2026/3/31 18:47
 * @description: 终端操作工具
 */

@Component
public class TerminalOperationTool {

    @Tool(description = "在终端中执行命令")
    public String executeTerminalCommand(@ToolParam(description = "要在终端中执行的命令") String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
//            Process process = Runtime.getRuntime().exec(command);
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

