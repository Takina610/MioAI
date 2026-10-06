package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.READ;

/**
 * Read 工具（zcode handlers/read.ts 的对位移植）：
 * cat -n 行号格式、默认 2000 行、256KB 上限、25000 token 预算（超限整读降级部分视图）、
 * 未变更重读拦截、空文件/越界/不存在/二进制的逐字文案。
 * <p>图片分支（zcode read-image.ts 的对位移植）：png/jpg/jpeg/gif/webp 在文本
 * 检查之前拦截，SFTP 读字节 → ImagePrepare 预算预处理 → 媒体结果，模型可见文本
 * 为 [Attached &lt;mime&gt;: Read image] 占位，图像由投影层拆成后置 user 消息投递。
 */
final class ReadTool {

    private static final int DEFAULT_MAX_LINES = 2000;
    private static final long MAX_FILE_SIZE_BYTES = 256 * 1024;
    private static final int MAX_OUTPUT_TOKENS = 25000;

    private static final List<String> BINARY_EXTENSIONS = List.of(
            "7z", "a", "bin", "bz2", "class", "dll", "dmg", "dylib", "exe", "gz", "jar",
            "o", "pyc", "rar", "so", "tar", "tgz", "wasm", "zip");

    private ReadTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "file_path":{"type":"string","description":"The absolute path to the file to read"},
                "offset":{"type":"integer","description":"The line number to start reading from. Only provide if the file is too large to read at once"},
                "limit":{"type":"integer","description":"The number of lines to read. Only provide if the file is too large to read at once"}},
                "required":["file_path"]}""";
        return ToolEntry.ofReadOnly("Read", READ, schema, ReadTool::execute);
    }

    static ToolResult execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String rawPath = Args.str(input, "file_path");
        if (rawPath == null) {
            throw new ToolUseFailure(12, "Tool path must not be empty");
        }
        Integer offset = Args.intVal(input, "offset");
        Integer limit = Args.intVal(input, "limit");
        SandboxFs fs = ctx.fs;
        String path;
        try {
            path = fs.resolve(rawPath);
        } catch (IllegalArgumentException e) {
            throw new ToolUseFailure(12, "Cannot read '" + rawPath + "': " + e.getMessage());
        }

        SandboxFs.FileStat stat = fs.stat(path);
        if (!stat.exists()) {
            throw new ToolUseFailure(2, notFoundMessage(fs, rawPath, path));
        }
        if (stat.directory()) {
            throw new ToolUseFailure(11, "Error: EISDIR: illegal operation on a directory, read");
        }
        String imageMime = inferImageMime(path);
        if (imageMime != null) {
            return readImage(fs, path, imageMime, stat);
        }
        String extension = extensionOf(path);
        if (BINARY_EXTENSIONS.contains(extension)) {
            throw new ToolUseFailure(14, "This tool cannot read binary files. The file appears to be a binary "
                    + extension + " file. Try using Bash to inspect or extract it instead.");
        }
        if (stat.size() > MAX_FILE_SIZE_BYTES) {
            throw new ToolUseFailure(15, "File is too large to read (" + stat.size() + " bytes). "
                    + "Maximum readable file size is 256KB. Use Grep or Bash to work with large files.");
        }

        int startLine = offset != null && offset > 0 ? offset : 1;
        int lineBudget = limit != null && limit > 0 ? limit : DEFAULT_MAX_LINES;
        int endLine = startLine + lineBudget - 1;

        // 未变更重读拦截（zcode file_unchanged）：同范围、文件未变、非部分视图
        ReadFileState.FileView view = ctx.readFileState.get(path);
        if (view != null && !view.partial() && view.startLine() == startLine
                && view.endLine() == endLine
                && view.mtimeSec() == stat.mtimeSec() && view.size() == stat.size()) {
            return ToolResult.of("Wasted call — file unchanged since your last Read. "
                    + "Refer to that earlier tool_result instead.");
        }

        List<String> lines = fs.readLines(path, startLine, endLine);
        int totalLines = fs.countLines(path);
        if (lines.isEmpty()) {
            if (startLine > 1 && totalLines == 0 && stat.size() == 0) {
                return ToolResult.of(
                        "<system-reminder>Warning: the file exists but the contents are empty.</system-reminder>");
            }
            if (startLine > Math.max(totalLines, 1)) {
                return ToolResult.of("Warning: the file exists but is shorter than the provided offset ("
                        + startLine + "). The file has " + totalLines + " lines.");
            }
            return ToolResult.of(
                    "<system-reminder>Warning: the file exists but the contents are empty.</system-reminder>");
        }

        boolean wholeFile = offset == null && limit == null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(startLine + i).append('\t').append(lines.get(i)).append('\n');
        }
        String rendered = sb.toString();
        long tokens = rendered.length() / 4;
        boolean partial = false;
        if (tokens > MAX_OUTPUT_TOKENS && wholeFile) {
            // zcode 降级：整读超预算时二分保留 85% 预算的前缀，转为部分视图
            int budgetChars = (int) (MAX_OUTPUT_TOKENS * 0.85 * 4);
            int kept = 0;
            int used = 0;
            for (int i = 0; i < lines.size(); i++) {
                int cost = String.valueOf(startLine + i).length() + lines.get(i).length() + 2;
                if (used + cost > budgetChars) {
                    break;
                }
                used += cost;
                kept++;
            }
            StringBuilder partialSb = new StringBuilder();
            partialSb.append("<system-reminder>The file is too large to display in full (")
                    .append(tokens).append(" estimated tokens, limit ").append(MAX_OUTPUT_TOKENS)
                    .append("). Showing a partial view of lines ").append(startLine).append('-')
                    .append(startLine + kept - 1).append(" of ").append(totalLines)
                    .append(". Use Read with offset ").append(startLine + kept)
                    .append(" and limit ").append(DEFAULT_MAX_LINES)
                    .append(" to continue, or use a search tool to find a specific section.</system-reminder>\n");
            for (int i = 0; i < kept; i++) {
                partialSb.append(startLine + i).append('\t').append(lines.get(i)).append('\n');
            }
            rendered = partialSb.toString();
            partial = true;
        } else if (tokens > MAX_OUTPUT_TOKENS) {
            throw new ToolUseFailure(16, "File content (" + tokens + " tokens) exceeds maximum allowed tokens ("
                    + MAX_OUTPUT_TOKENS + "). Please use the offset and limit parameters to read a smaller portion.");
        }

        ctx.readFileState.put(path, new ReadFileState.FileView(
                stat.mtimeSec(), stat.size(), startLine, startLine + lines.size() - 1, partial));
        return ToolResult.of(rendered);
    }

    /**
     * 图片分支（zcode readImageFile）：20MB 输入上限 → SFTP 字节读取 →
     * 预算预处理 → [Attached &lt;mime&gt;: Read image] 文本 + 媒体负载。
     * 图片读取不进入 readFileState（zcode 图片路径同样不缓存）。
     */
    private static ToolResult readImage(SandboxFs fs, String path, String mime, SandboxFs.FileStat stat) {
        if (stat.size() > ImagePrepare.MAX_INPUT_BYTES) {
            throw new ToolUseFailure(15, "Image file is too large to read (" + stat.size() + " bytes). "
                    + "Maximum image input size is 20MB.");
        }
        byte[] data;
        try {
            data = fs.readFileBytes(path, ImagePrepare.MAX_INPUT_BYTES);
        } catch (IllegalStateException e) {
            throw new ToolUseFailure(15, e.getMessage());
        }
        ImagePrepare.Prepared prepared;
        try {
            prepared = ImagePrepare.prepare(data, mime);
        } catch (ImagePrepare.ImagePrepareException e) {
            throw new ToolUseFailure(15, e.getMessage());
        }
        ToolMedia media = new ToolMedia(prepared.mimeType(),
                Base64.getEncoder().encodeToString(prepared.data()),
                prepared.originalSize(), prepared.data().length,
                prepared.resized(), prepared.compressed(),
                prepared.originalWidth(), prepared.originalHeight(),
                prepared.width(), prepared.height());
        return ToolResult.withMedia("[Attached " + prepared.mimeType() + ": Read image]", media);
    }

    /** zcode inferImageMimeFromPath：扩展名推断图片 MIME，非图片返回 null */
    private static String inferImageMime(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    /** zcode 未找到文案：附当前目录与相似名建议（同干名优先，其次编辑距离 ≤3） */
    private static String notFoundMessage(SandboxFs fs, String rawPath, String path) {
        String cwd = fs.workdirDisplay();
        String parent = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ".";
        String base = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        String suggestion = null;
        for (String name : fs.listNames(parent)) {
            if (name.equals(base)) {
                continue;
            }
            String nameBase = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
            String baseStem = base.contains(".") ? base.substring(0, base.lastIndexOf('.')) : base;
            if (nameBase.equals(baseStem)) {
                suggestion = parent.equals(".") ? name : parent + "/" + name;
                break;
            }
        }
        if (suggestion == null) {
            int best = 4;
            for (String name : fs.listNames(parent)) {
                int distance = levenshtein(base, name);
                if (distance <= 3 && distance < best) {
                    best = distance;
                    suggestion = parent.equals(".") ? name : parent + "/" + name;
                }
            }
        }
        String message = "File does not exist. Note: your current working directory is " + cwd + ".";
        if (suggestion != null) {
            message += " Did you mean " + suggestion + "?";
        }
        return message;
    }

    private static String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i) == b.charAt(j) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
