package com.mio.ai.framework.tools.sandbox;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/10/4
 * @description: Linux 沙箱工具：在独立服务器上真实执行命令与写文件。
 * 参考 pi 的 bash 工具描述风格——把截断策略、超时、适用场景直接写进 description，
 * 让模型知道"该不该用、用错了会发生什么"。
 */
@Component
@ConditionalOnProperty(prefix = "mio.ai.sandbox", name = "enabled", havingValue = "true")
public class SandboxTool {

    private final SshSandboxClient client;

    public SandboxTool(SandboxProperties properties) {
        this.client = new SshSandboxClient(properties);
    }

    @Tool(description = "在 Linux 沙箱服务器上执行 shell 命令（bash）。适合：运行/调试代码（python3、node 等）、"
            + "处理数据、验证计算结果、批量文本变换。所有命令在固定工作目录执行，工作目录内容跨命令保留。"
            + "stdout 与 stderr 合并返回，过长时保留末尾；默认超时 120 秒，可用参数延长到最多 600 秒。"
            + "读文件可用 cat、找文件可用 find/ls，不需要单独的读取工具")
    public String runInSandbox(
            @ToolParam(description = "要执行的 bash 命令，可用 && 串联多步") String command,
            @ToolParam(description = "超时秒数（可选，默认 120，最大 600）", required = false) Integer timeoutSeconds) {
        return client.run(command, timeoutSeconds);
    }

    @Tool(description = "把文本文件写入沙箱工作目录（自动创建子目录，同名覆盖）。"
            + "先 writeSandboxFile 再用 runInSandbox 执行，是跑代码的标准流程")
    public String writeSandboxFile(
            @ToolParam(description = "工作目录内的相对文件路径，例如 scripts/analyze.py") String fileName,
            @ToolParam(description = "文件的完整文本内容") String content) {
        return client.writeFile(fileName, content);
    }
}
