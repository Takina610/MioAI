package com.mio.ai.framework.tools;

import com.mio.ai.framework.sandbox.SandboxSession;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用文件原语（参考 zcode/pi 的 read/write/edit）：读写与精确编辑沙箱工作目录内的文件。
 * edit 在服务端做精确字符串替换并校验唯一性——错误信息面向模型，让它能自我修正。
 */
@Component
public class SandboxFileTool {

    /** 单次读取的行数上限：超过保留前段并提示用 offset 继续（头部截断，文章/代码开头更有信息量） */
    private static final int READ_LIMIT_DEFAULT = 800;

    private static final int READ_LIMIT_MAX = 2000;

    private final SandboxSession session;

    public SandboxFileTool(SandboxSession session) {
        this.session = session;
    }

    @Tool(description = "Reads a file from your sandbox working directory. "
            + "Results use cat -n format, with line numbers starting at 1. "
            + "Reads up to 800 lines by default; longer files report the total line count — continue with the offset parameter. "
            + "You can optionally specify a line offset and limit (especially handy for long files), "
            + "but it's recommended to read the whole file by not providing these parameters. "
            + "A missing file or empty file returns an error rather than content. "
            + "Do NOT re-read a file you just edited to verify — editFile/writeFile would have errored if the change failed.")
    public String readFile(
            @ToolParam(description = "工作目录内的相对路径，如 scripts/analyze.py") String path,
            @ToolParam(description = "起始行号（1 起，可选）", required = false) Integer offset,
            @ToolParam(description = "本次最多读取的行数（可选，最大 2000）", required = false) Integer limit) {
        try {
            String content = session.readFile(path);
            String[] lines = content.split("\n", -1);
            int from = Math.max(offset != null ? offset : 1, 1);
            int max = limit != null ? Math.min(limit, READ_LIMIT_MAX) : READ_LIMIT_DEFAULT;
            int to = Math.min(from + max - 1, lines.length);

            StringBuilder sb = new StringBuilder();
            if (from > lines.length) {
                return "文件共 " + lines.length + " 行，offset " + from + " 超出范围";
            }
            for (int i = from; i <= to; i++) {
                sb.append(String.format("%6d\t%s%n", i, lines[i - 1]));
            }
            if (to < lines.length) {
                sb.append("[共 ").append(lines.length).append(" 行，已显示 ").append(from).append("-")
                        .append(to).append("，继续读用 offset=").append(to + 1).append("]");
            }
            return sb.toString();
        } catch (Exception e) {
            return "读取失败: " + e.getMessage();
        }
    }

    @Tool(description = "Writes a file to your sandbox working directory, overwriting if one exists "
            + "(parent directories are created automatically). "
            + "When to use: creating a new file, or fully replacing one you've already read. "
            + "For partial changes, use editFile instead.")
    public String writeFile(
            @ToolParam(description = "工作目录内的相对路径") String path,
            @ToolParam(description = "文件的完整文本内容") String content) {
        try {
            session.writeFile(path, content);
            return "已写入 " + path + "（" + (content == null ? 0 : content.length()) + " 字符）";
        } catch (Exception e) {
            return "写入失败: " + e.getMessage();
        }
    }

    @Tool(description = "Performs exact string replacement in a file in your sandbox working directory. "
            + "You must readFile the file in this conversation before editing, or the call will fail. "
            + "`oldText` must match the file exactly, including indentation, and be unique — the edit fails otherwise "
            + "(the error lists match positions; add more surrounding context to make it unique, or set replaceAll=true to replace every occurrence). "
            + "Strip the readFile line-number prefix before matching.")
    public String editFile(
            @ToolParam(description = "工作目录内的相对路径") String path,
            @ToolParam(description = "要替换的原文本（必须精确匹配）") String oldText,
            @ToolParam(description = "替换后的新文本") String newText,
            @ToolParam(description = "是否替换全部匹配（可选，默认只替换且要求唯一）", required = false) Boolean replaceAll) {
        try {
            String content = session.readFile(path);
            if (!content.contains(oldText)) {
                return "oldText 在文件中不存在。请先 readFile 核对原文（注意缩进与空白），再重试";
            }
            boolean all = replaceAll != null && replaceAll;
            int count = countOccurrences(content, oldText);
            if (count > 1 && !all) {
                return "oldText 有 " + count + " 处匹配（行 " + matchLines(content, oldText)
                        + "）。请加入更多上下文使其唯一，或设 replaceAll=true";
            }
            String next = all
                    ? content.replace(oldText, newText)
                    : content.replaceFirst(Pattern.quote(oldText), Matcher.quoteReplacement(newText));
            session.writeFile(path, next);
            return "已替换 " + (all ? count : 1) + " 处，文件已更新";
        } catch (Exception e) {
            return "编辑失败: " + e.getMessage();
        }
    }

    private int countOccurrences(String content, String target) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    /** 各匹配首次出现的行号（1 起），逗号分隔 */
    private String matchLines(String content, String target) {
        java.util.List<Integer> lines = new java.util.ArrayList<>();
        int line = 1;
        int index = 0;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
            if (i == index) {
                if (content.startsWith(target, i)) {
                    lines.add(line);
                    index += target.length();
                } else {
                    index++;
                }
            }
        }
        return lines.stream().map(String::valueOf).limit(10).reduce((a, b) -> a + "," + b).orElse("?");
    }
}
