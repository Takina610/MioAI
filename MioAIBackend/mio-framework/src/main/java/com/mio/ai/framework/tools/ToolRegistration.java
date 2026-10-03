package com.mio.ai.framework.tools;

import com.mio.ai.framework.tools.CommonTools.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: Takina
 * @date: 2026/3/31 17:13
 * @description: 集中的工具注册类（MioBot 内置工具池）
 */
@Configuration(enforceUniqueMethods = false)
public class ToolRegistration {

    @Bean(name = "commonTools")
    public ToolCallback[] commonTools(FileOperationTool fileOperationTool,
                                      PDFGenerationTool pdfGenerationTool,
                                      ResourceDownloadTool resourceDownloadTool,
                                      TerminalOperationTool terminalOperationTool,
                                      WebScrapingTool webScrapingTool,
                                      WebSearchTool webSearchTool,
                                      ImageSearchTool imageSearchTool
    ) {
        return ToolCallbacks.from(
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminalOperationTool,
                webScrapingTool,
                webSearchTool,
                imageSearchTool
        );
    }
}
