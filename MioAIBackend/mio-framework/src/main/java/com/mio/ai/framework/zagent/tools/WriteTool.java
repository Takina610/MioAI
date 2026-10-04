package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.tools.SandboxFs.FileStat;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.WRITE;

/**
 * Write 工具（zcode handlers/write.ts 的对位移植）：
 * 读前门禁（未读/过期拒绝）、写后登记读状态（无需回读）、成功文案逐字对齐。
 */
final class WriteTool {

    private WriteTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "file_path":{"type":"string","description":"The absolute path to the file to write (must be absolute, not relative)"},
                "content":{"type":"string","description":"The content to write to the file"}},
                "required":["file_path","content"]}""";
        return ToolEntry.ofMutable("Write", WRITE, schema, 30_000, WriteTool::execute);
    }

    static String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String rawPath = Args.str(input, "file_path");
        String content = input.hasNonNull("content") ? input.get("content").asText() : "";
        if (rawPath == null) {
            throw new ToolUseFailure(13, "Tool path must not be empty");
        }
        SandboxFs fs = ctx.fs;
        String path;
        try {
            path = fs.resolve(rawPath);
        } catch (IllegalArgumentException e) {
            throw new ToolUseFailure(13, "Cannot write '" + rawPath + "': " + e.getMessage());
        }

        boolean exists = fs.stat(path).exists();
        if (exists) {
            guardFreshness(ctx, fs, path);
        }
        fs.write(path, content);
        FileStat after = fs.stat(path);
        ctx.readFileState.put(path, new ReadFileState.FileView(
                after.mtimeSec(), after.size(), 1, Integer.MAX_VALUE, false));
        if (!exists) {
            return "File created successfully at: " + path
                    + " (file state is current in you context — no need to Read it back)";
        }
        return "The file " + path + " has been updated successfully. "
                + "(file state is current in you context — no need to Read it back)";
    }

    /** zcode 读前新鲜度门禁：未读拒绝；读过但文件已变（mtime/size 不符）拒绝 */
    static void guardFreshness(ToolContext ctx, SandboxFs fs, String path) {
        ReadFileState.FileView view = ctx.readFileState.get(path);
        if (view == null) {
            throw new ToolUseFailure(6, "File has not been read yet. Read it first before writing to it.");
        }
        if (view.partial()) {
            throw new ToolUseFailure(6, "File has only been read partially (a partial view cannot be edited). "
                    + "Read the full file first before writing to it.");
        }
        FileStat current = fs.stat(path);
        if (ctx.readFileState.isStale(path, current.mtimeSec(), current.size())) {
            throw new ToolUseFailure(7, "File has been modified since read, either by the user or by a linter. "
                    + "Read it again before attempting to write it.");
        }
    }
}
