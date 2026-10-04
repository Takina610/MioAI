package com.mio.ai.framework.tools.CommonTools;

import cn.hutool.core.io.FileUtil;
import com.mio.ai.common.constant.SystemConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/31 17:04
 * @description: Agent 工作区文件工具（读写/追加/列表/删除）。
 * 所有文件都落在服务端工作区内，文件名自动归一化（清理非法字符），拒绝跳出工作区。
 */
@Component
public class FileOperationTool {

    @Tool(description = "读取工作区文件的内容。fileName 为相对工作区的文件名（可含子目录），不是绝对路径")
    public String readFile(@ToolParam(description = "文件名，例如：上海旅游计划.md") String fileName) {
        try {
            File file = resolve(fileName);
            if (!file.isFile()) {
                return "文件不存在: " + fileName + "。可先调用 listWorkspaceFiles 查看已有文件。";
            }
            return FileUtil.readUtf8String(file);
        } catch (Exception e) {
            return "读取文件错误: " + e.getMessage();
        }
    }

    @Tool(description = "把内容完整写入工作区文件（同名覆盖）。注意：工作区文件用户看不到，"
            + "要给用户产出可下载的最终文档请直接调用 generatePDF 并传完整内容，不要先写中间文件再转换")
    public String writeFile(@ToolParam(description = "文件名，例如：草稿.md") String fileName,
                            @ToolParam(description = "要写入文件的完整内容") String content) {
        try {
            File file = resolve(fileName);
            FileUtil.mkdir(file.getParentFile());
            FileUtil.writeUtf8String(content == null ? "" : content, file);
            return "文件写入成功: " + file.getName() + "（" + (content == null ? 0 : content.length()) + " 字符）";
        } catch (Exception e) {
            return "文件写入错误: " + e.getMessage();
        }
    }

    @Tool(description = "在文件末尾追加内容（不影响原有内容，文件不存在时自动创建）。"
            + "写长文档时建议分多次 append，避免每次从头重写整篇")
    public String appendFile(@ToolParam(description = "文件名，例如：上海旅游计划.md") String fileName,
                             @ToolParam(description = "要追加的内容") String content) {
        try {
            File file = resolve(fileName);
            FileUtil.mkdir(file.getParentFile());
            FileUtil.appendString(content == null ? "" : content, file.getAbsolutePath(), "UTF-8");
            return "已追加 " + (content == null ? 0 : content.length()) + " 字符到 " + file.getName();
        } catch (Exception e) {
            return "追加写入错误: " + e.getMessage();
        }
    }

    @Tool(description = "列出工作区已存在的文件（名称与大小），写入/读取前可先查看")
    public String listWorkspaceFiles() {
        File base = new File(SystemConstant.FILE_SAVE_DIR).getAbsoluteFile();
        if (!base.isDirectory()) {
            return "工作区暂无任何文件";
        }
        List<String> lines = new ArrayList<>();
        appendFileLines(base, base, lines, 0);
        return lines.isEmpty() ? "工作区暂无任何文件" : String.join("\n", lines);
    }

    @Tool(description = "删除工作区中不再需要的文件")
    public String deleteFile(@ToolParam(description = "要删除的文件名") String fileName) {
        try {
            File file = resolve(fileName);
            if (!file.isFile()) {
                return "文件不存在: " + fileName;
            }
            FileUtil.del(file);
            return "已删除: " + file.getName();
        } catch (Exception e) {
            return "删除文件错误: " + e.getMessage();
        }
    }

    /**
     * 文件名归一化：统一分隔符、清理 Windows 非法字符，并拒绝跳出工作区目录
     */
    private static File resolve(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String normalized = fileName.replace("\\", "/").replaceAll("[\\:*?\"<>|]", "_").trim();
        File base = new File(SystemConstant.FILE_SAVE_DIR).getAbsoluteFile();
        File file = new File(base, normalized).getCanonicalFile();
        if (!file.getPath().startsWith(base.getCanonicalPath())) {
            throw new IllegalArgumentException("禁止访问工作区之外的路径: " + fileName);
        }
        return file;
    }

    /** 递归列出工作区文件（最多三层，防止意外遍历过深） */
    private static void appendFileLines(File root, File dir, List<String> lines, int depth) {
        if (depth > 3) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String relative = root.toPath().relativize(child.toPath()).toString().replace('\\', '/');
            if (child.isDirectory()) {
                lines.add(relative + "/");
                appendFileLines(root, child, lines, depth + 1);
            } else {
                lines.add(relative + "（" + (child.length() / 1024) + " KB）");
            }
        }
    }
}
