package com.mio.ai.framework.zagent.tools;

import java.util.List;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.GLOB;
import static com.mio.ai.framework.zagent.tools.ToolDescriptions.GLOB_PATH_PARAM;

/**
 * Glob 工具（zcode handlers/glob.ts 的对位移植）：
 * mtime 降序、上限 100 条、截断提示与空结果文案对齐。
 */
final class GlobTool {

    private static final int MAX_RESULTS = 100;

    private GlobTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "pattern":{"type":"string","description":"The glob pattern to match files against"},
                "path":{"type":"string","description":%s}},
                "required":["pattern"]}""".formatted('"' + GLOB_PATH_PARAM + '"');
        return ToolEntry.ofReadOnly("Glob", GLOB, schema, GlobTool::execute);
    }

    static String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String pattern = Args.str(input, "pattern");
        if (pattern == null) {
            throw new ToolUseFailure(1, "Pattern must not be empty");
        }
        // 换模型兼容：path 误传字面量 "undefined"/"null" 时视为未提供（默认当前目录）
        String path = Args.cleanPath(Args.str(input, "path"));
        List<String> matches = ctx.fs.glob(path, pattern, MAX_RESULTS);
        if (matches.isEmpty()) {
            return "No files found";
        }
        boolean truncated = matches.size() > MAX_RESULTS;
        List<String> shown = truncated ? matches.subList(0, MAX_RESULTS) : matches;
        StringBuilder sb = new StringBuilder();
        for (String match : shown) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(match);
        }
        if (truncated) {
            sb.append("\n(Results are truncated. Consider using a more specific path or pattern.)");
        }
        return sb.toString();
    }
}
