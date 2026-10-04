package com.mio.ai.framework.tools;

import com.mio.ai.framework.sandbox.SandboxSession;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 通用文件查找原语（参考 zcode/pi 的 glob/grep）：按文件名模式或内容正则搜索沙箱工作目录。
 */
@Component
public class SandboxSearchTool {

    private static final int MAX_RESULTS = 100;

    private final SandboxSession session;

    public SandboxSearchTool(SandboxSession session) {
        this.session = session;
    }

    @Tool(description = "按文件名 glob 模式查找沙箱工作目录内的文件（递归），如 *.py、report-*。"
            + "不确定文件路径时先用这个")
    public String glob(
            @ToolParam(description = "文件名 glob 模式") String pattern) {
        try {
            String safe = session.resolveRelative(".");
            String command = "find . -not -path '*/node_modules/*' -not -path '*/.git/*' -name "
                    + "'" + pattern.replace("'", "'\\''") + "' 2>/dev/null | head -" + MAX_RESULTS;
            String output = session.exec(command, null).output().strip();
            return output.isEmpty() ? "没有匹配的文件" : output + "\n(最多显示 " + MAX_RESULTS + " 条)";
        } catch (Exception e) {
            return "查找失败: " + e.getMessage();
        }
    }

    @Tool(description = "在沙箱工作目录内按正则搜索文件内容（递归，输出 文件:行号:内容）。"
            + "定位代码/数据里的关键字用它；单行超长会截断到 300 字符")
    public String grep(
            @ToolParam(description = "正则表达式（ERE 语法）") String pattern,
            @ToolParam(description = "限定搜索的子目录或文件（可选，默认整个工作目录）", required = false) String path,
            @ToolParam(description = "只搜索的文件名 glob，如 *.py（可选）", required = false) String include) {
        try {
            String target = path != null && !path.isBlank() ? session.resolveRelative(path) : ".";
            StringBuilder command = new StringBuilder("grep -rnEI");
            if (include != null && !include.isBlank()) {
                command.append(" --include='").append(include.replace("'", "'\\''")).append("'");
            }
            command.append(" -- '").append(pattern.replace("'", "'\\''")).append("' ")
                    .append(target.replace("'", "'\\''"))
                    .append(" 2>/dev/null | cut -c1-300 | head -").append(MAX_RESULTS);
            String output = session.exec(command.toString(), null).output().strip();
            return output.isEmpty() ? "没有匹配的内容" : output;
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }
}
