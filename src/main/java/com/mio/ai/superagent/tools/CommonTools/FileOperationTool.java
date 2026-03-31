package com.mio.ai.superagent.tools.CommonTools;

import cn.hutool.core.io.FileUtil;
import com.mio.ai.common.constant.SystemConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 17:04
 * @description: 文件操作工具类（提供文件读写功能）
 */

@Component
public class FileOperationTool {

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        String filePath = SystemConstant.FILE_SAVE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of the file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content
    ) {
        String filePath = SystemConstant.FILE_SAVE_DIR + "/" + fileName;

        try {
            // 创建目录
            FileUtil.mkdir(SystemConstant.FILE_SAVE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "File written successfully to: " + filePath;
        } catch (Exception e) {
            return "Error writing to file: " + e.getMessage();
        }
    }
}

