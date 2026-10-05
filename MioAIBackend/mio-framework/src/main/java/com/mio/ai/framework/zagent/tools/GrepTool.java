package com.mio.ai.framework.zagent.tools;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.zagent.tools.SandboxFs.ExecResult;

import java.util.ArrayList;
import java.util.List;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.GREP;

/**
 * Grep 工具（zcode handlers/grep.ts 的对位移植）：
 * 三种 output_mode、上下文行、行号、分页（默认 250 条）与各类结果文案对齐。
 * 后端为 grep -rE（沙箱无 ripgrep 时的等价实现）。
 */
final class GrepTool {

    private static final int DEFAULT_HEAD_LIMIT = 250;

    private GrepTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "pattern":{"type":"string","description":"The regular expression pattern to search for in file contents"},
                "path":{"type":"string","description":"File or directory to search in (rg PATH). Defaults to current working directory."},
                "glob":{"type":"string","description":"Glob pattern to filter files (e.g. \\"*.js\\",\\"*.{ts,tsx}\\") - maps to grep --include"},
                "output_mode":{"type":"string","enum":["content","files_with_matches","count"],"description":"Output mode: content (matching lines), files_with_matches (paths only), or count. Requires output_mode: content for context parameters. Defaults to files_with_matches."},
                "-A":{"type":"number","description":"Lines to show after each match. Requires output_mode: content."},
                "-B":{"type":"number","description":"Lines to show before each match. Requires output_mode: content."},
                "-C":{"type":"number","description":"Lines of context around each match. Requires output_mode: content."},
                "-i":{"type":"boolean","description":"Case insensitive search"},
                "head_limit":{"type":"number","description":"Limit output to first N matches or entries. Defaults to 250. Use 0 for unlimited."},
                "offset":{"type":"number","description":"Skip first N matches or entries. Defaults to 0."},
                "type":{"type":"string","description":"File type to search (grep --include derived). Common types: js, py, rust, go, java, etc."}},
                "required":["pattern"]}""";
        return ToolEntry.ofReadOnly("Grep", GREP, schema, GrepTool::execute);
    }

    static String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String pattern = Args.str(input, "pattern");
        if (pattern == null) {
            throw new ToolUseFailure(1, "Pattern must not be empty");
        }
        // 换模型兼容：path 误传 "undefined"/"null" 字符串时视为未提供（默认当前目录）
        String path = Args.cleanPath(Args.str(input, "path"));
        String glob = Args.str(input, "glob");
        String type = Args.str(input, "type");
        String outputMode = StrUtil.blankToDefault(Args.str(input, "output_mode"), "files_with_matches");
        // 上下文参数宽容归一：模型常写完整词 after/before/context（zcode resolveInput 语义）
        Integer after = Args.intVal(input, "-A");
        if (after == null) {
            after = Args.intVal(input, "after");
        }
        Integer before = Args.intVal(input, "-B");
        if (before == null) {
            before = Args.intVal(input, "before");
        }
        Integer context = Args.intVal(input, "-C");
        if (context == null) {
            context = Args.intVal(input, "context");
        }
        boolean caseInsensitive = Args.bool(input, "-i") || Args.bool(input, "ignore_case");
        Integer headLimit = Args.intVal(input, "head_limit");
        Integer offset = Args.intVal(input, "offset");
        int limit = headLimit != null && headLimit >= 0 ? headLimit : DEFAULT_HEAD_LIMIT;
        int skip = offset != null && offset > 0 ? offset : 0;

        if (!"content".equals(outputMode) && (after != null || before != null || context != null)) {
            throw new ToolUseFailure(2, "Context parameters (-A, -B, -C) require output_mode: content.");
        }

        String flags = caseInsensitive ? "-i " : "";
        String mode = switch (outputMode) {
            case "content" -> {
                StringBuilder f = new StringBuilder(flags);
                if (after != null) {
                    f.append("-A ").append(after).append(' ');
                }
                if (before != null) {
                    f.append("-B ").append(before).append(' ');
                }
                if (context != null) {
                    f.append("-C ").append(context).append(' ');
                }
                f.append("-n");
                yield f.toString();
            }
            case "count" -> flags + "-c";
            default -> flags + "-l";
        };
        String include = glob != null ? glob : typeToInclude(type);
        ExecResult result = ctx.fs.grep(mode.trim(), include, pattern, path);
        if (result.exitCode() != null && result.exitCode() == 2) {
            throw new ToolUseFailure(3, "Grep failed: " + result.output());
        }
        List<String> lines = result.output() == null || result.output().isBlank()
                ? List.of() : SandboxFs.splitLines(result.output());

        return switch (outputMode) {
            case "content" -> renderContent(ctx, lines, limit, skip);
            case "count" -> renderCount(lines, limit, skip);
            default -> renderFiles(ctx, lines, limit, skip);
        };
    }

    private static String renderContent(ToolContext ctx, List<String> lines, int limit, int skip) {
        if (lines.isEmpty()) {
            return "No matches found";
        }
        List<String> page = paginate(lines, limit, skip);
        StringBuilder sb = new StringBuilder();
        for (String line : page) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(ctx.fs.stripRoot(line));
        }
        appendPagination(sb, limit, skip);
        return sb.toString();
    }

    private static String renderCount(List<String> lines, int limit, int skip) {
        List<String> page = paginate(lines, limit, skip);
        long total = page.stream()
                .mapToLong(line -> {
                    int idx = line.lastIndexOf(':');
                    try {
                        return idx < 0 ? 0 : Long.parseLong(line.substring(idx + 1));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .sum();
        StringBuilder sb = new StringBuilder();
        for (String line : page) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        sb.append("\nFound ").append(total).append(" total occurrences across ")
                .append(page.size()).append(" files.");
        return sb.toString();
    }

    private static String renderFiles(ToolContext ctx, List<String> lines, int limit, int skip) {
        if (lines.isEmpty()) {
            return "No files found";
        }
        List<String> page = paginate(lines, limit, skip);
        StringBuilder sb = new StringBuilder("Found ").append(page.size()).append(" files\n");
        for (String line : page) {
            sb.append(ctx.fs.stripRoot(line)).append('\n');
        }
        appendPagination(sb, limit, skip);
        return sb.toString().stripTrailing();
    }

    private static List<String> paginate(List<String> lines, int limit, int skip) {
        int from = Math.min(skip, lines.size());
        int to = limit == 0 ? lines.size() : Math.min(from + limit, lines.size());
        return new ArrayList<>(lines.subList(from, to));
    }

    private static void appendPagination(StringBuilder sb, int limit, int skip) {
        if (limit > 0 && sb.length() > 0) {
            sb.append("\n\n[Showing results with pagination = limit: ").append(limit)
                    .append(", offset: ").append(skip).append(']');
        }
    }

    /** 常见类型名 → --include 通配 */
    private static String typeToInclude(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return "*." + type.trim();
    }
}
