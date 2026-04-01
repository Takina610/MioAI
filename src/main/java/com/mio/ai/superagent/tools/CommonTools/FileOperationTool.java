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

    @Tool(description = "从文件中读取内容")
    public String readFile(@ToolParam(description = "要读取的文件名") String fileName) {
        String filePath = SystemConstant.FILE_SAVE_DIR + "/" + fileName;
        try {
            return FileUtil.readUtf8String(filePath);
        } catch (Exception e) {
            return "读取文件错误: " + e.getMessage();
        }
    }

    @Tool(description = "向文件写入内容")
    public String writeFile(@ToolParam(description = "要写入的文件名") String fileName,
                            @ToolParam(description = "要写入文件的内容") String content
    ) {
        String filePath = SystemConstant.FILE_SAVE_DIR + "/" + fileName;

        try {
            // 创建目录
            FileUtil.mkdir(SystemConstant.FILE_SAVE_DIR);
            FileUtil.writeUtf8String(content, filePath);
            return "文件写入成功: " + filePath;
        } catch (Exception e) {
            return "文件写入错误: " + e.getMessage();
        }
    }
}

