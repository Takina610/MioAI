package com.mio.ai.framework.tools.CommonTools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.mio.ai.common.constant.SystemConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author: Takina
 * @date: 2026/3/31 18:45
 * @description: 资源下载工具
 */

@Component
public class ResourceDownloadTool {

    @Tool(description = "从指定URL下载资源")
    public String downloadResource(@ToolParam(description = "要下载资源的URL") String url,
                                   @ToolParam(description = "保存下载资源的文件名") String fileName) {
        String fileDir = SystemConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 使用 Hutool 的 downloadFile 方法下载资源
            HttpUtil.downloadFile(url, new File(filePath));
            return "资源下载成功: " + filePath;
        } catch (Exception e) {
            return "下载资源错误: " + e.getMessage();
        }
    }
}

