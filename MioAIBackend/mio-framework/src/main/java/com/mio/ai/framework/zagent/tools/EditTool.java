package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.tools.EditMatcher.Match;
import com.mio.ai.framework.zagent.tools.SandboxFs.FileStat;

import java.util.ArrayList;
import java.util.List;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.EDIT;

/**
 * Edit 工具（zcode handlers/edit.ts 的对位移植）：
 * 精确字符串替换、唯一性校验、replace_all、错误文案与错误码逐字对齐。
 */
final class EditTool {

    private EditTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "file_path":{"type":"string","description":"The absolute path to the file to modify"},
                "old_string":{"type":"string","description":"The text to replace"},
                "new_string":{"type":"string","description":"The text to replace it with (must be different from old_string)"},
                "replace_all":{"type":"boolean","description":"Replace all occurrences of old_string (default false)"}},
                "required":["file_path","old_string","new_string"]}""";
        return ToolEntry.ofMutable("Edit", EDIT, schema, 30_000, EditTool::execute);
    }

    static String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String rawPath = Args.str(input, "file_path");
        String oldString = Args.str(input, "old_string");
        String newString = input.hasNonNull("new_string") ? input.get("new_string").asText() : "";
        boolean replaceAll = Args.bool(input, "replace_all");
        if (rawPath == null) {
            throw new ToolUseFailure(13, "Tool path must not be empty");
        }
        if (oldString != null && oldString.equals(newString)) {
            throw new ToolUseFailure(1, "No changes to make: old_string and new_string are exactly the same.");
        }
        SandboxFs fs = ctx.fs;
        String path;
        try {
            path = fs.resolve(rawPath);
        } catch (IllegalArgumentException e) {
            throw new ToolUseFailure(13, "Cannot edit '" + rawPath + "': " + e.getMessage());
        }

        FileStat stat = fs.stat(path);
        boolean creating = !stat.exists() && (oldString == null || oldString.isEmpty());
        if (!stat.exists()) {
            if (creating) {
                fs.write(path, newString);
                return "File created successfully at: " + path
                        + " (file state is current in you context — no need to Read it back)";
            }
            throw new ToolUseFailure(4, "File does not exist. Note: your current working directory is "
                    + fs.workdirDisplay() + ".");
        }
        WriteTool.guardFreshness(ctx, fs, path);

        String content = fs.readAll(path);
        if (content.isEmpty() && (oldString == null || oldString.isEmpty())) {
            fs.write(path, newString);
            return "File created successfully at: " + path
                    + " (file state is current in you context — no need to Read it back)";
        }
        if (oldString != null && !oldString.isEmpty() && content.contains(oldString)) {
            return applyExact(fs, ctx, path, content, oldString, newString, replaceAll);
        }
        // 宽容匹配（缩进灵活）后仍未命中 → zcode 未找到文案
        List<String> lines = SandboxFs.splitLines(content);
        Match match = EditMatcher.find(lines, oldString, true);
        if (match == null) {
            throw new ToolUseFailure(8, "String to replace not found in file.\nString: " + oldString);
        }
        if (!replaceAll && EditMatcher.count(lines, oldString, true) > 1) {
            throw ambiguous(oldString);
        }
        return applyIndentFlexible(fs, ctx, path, lines, oldString, newString, replaceAll);
    }

    private static String applyExact(SandboxFs fs, ToolContext ctx, String path, String content,
                                     String oldString, String newString, boolean replaceAll) {
        int occurrences = countOccurrences(content, oldString);
        if (!replaceAll && occurrences > 1) {
            throw ambiguous(oldString);
        }
        String updated;
        if (replaceAll) {
            updated = content.replace(oldString, newString);
        } else {
            int idx = content.indexOf(oldString);
            updated = content.substring(0, idx) + newString + content.substring(idx + oldString.length());
        }
        fs.write(path, updated);
        refreshState(ctx, fs, path);
        return successMessage(path, replaceAll);
    }

    private static String applyIndentFlexible(SandboxFs fs, ToolContext ctx, String path,
                                              List<String> lines, String oldString, String newString,
                                              boolean replaceAll) {
        List<String> result = new ArrayList<>(lines);
        boolean replaced = replaceRange(result, oldString, newString, replaceAll);
        if (!replaced) {
            throw new ToolUseFailure(8, "String to replace not found in file.\nString: " + oldString);
        }
        fs.write(path, String.join("\n", result) + "\n");
        refreshState(ctx, fs, path);
        return successMessage(path, replaceAll);
    }

    /** 行级缩进灵活替换：命中区间整体替换为 new_string 的行 */
    private static boolean replaceRange(List<String> lines, String oldString, String newString, boolean replaceAll) {
        boolean replaced = false;
        while (true) {
            Match match = EditMatcher.find(lines, oldString, true);
            if (match == null) {
                return replaced;
            }
            List<String> replacement = newString.lines().toList();
            // 保留被替换首行的缩进（对齐 zcode 的替换归一化）
            String originalIndent = leadingIndent(lines.get(match.startLine()));
            List<String> adjusted = new ArrayList<>();
            for (int i = 0; i < replacement.size(); i++) {
                String line = replacement.get(i);
                if (i == 0 || line.isBlank()) {
                    adjusted.add(originalIndent + line);
                } else {
                    adjusted.add(originalIndent + stripCommon(replacement, line));
                }
            }
            lines.subList(match.startLine(), match.endLine() + 1).clear();
            lines.addAll(match.startLine(), adjusted);
            replaced = true;
            if (!replaceAll) {
                return true;
            }
        }
    }

    private static String stripCommon(List<String> replacement, String line) {
        String common = leadingIndent(replacement.get(0));
        return line.startsWith(common) && !common.isEmpty() ? line.substring(common.length()) : line;
    }

    private static String leadingIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    private static ToolUseFailure ambiguous(String oldString) {
        return new ToolUseFailure(9, "Found multiple matches of the string to replace, but replace_all is false. "
                + "To replace all occurrences, set replace_all to true. To replace only one occurrence, "
                + "please provide more context to uniquely identify the instance.\nString: " + oldString);
    }

    private static String successMessage(String path, boolean replaceAll) {
        String suffix = replaceAll
                ? " All occurrences were successfully replaced."
                : "";
        return "The file " + path + " has been updated successfully. (file state is current in the context"
                + " — no need to Read it back)" + suffix;
    }

    private static void refreshState(ToolContext ctx, SandboxFs fs, String path) {
        FileStat after = fs.stat(path);
        ctx.readFileState.put(path, new ReadFileState.FileView(
                after.mtimeSec(), after.size(), 1, Integer.MAX_VALUE, false));
    }

    private static int countOccurrences(String content, String oldString) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf(oldString, idx)) >= 0) {
            count++;
            idx += oldString.length();
        }
        return count;
    }
}
