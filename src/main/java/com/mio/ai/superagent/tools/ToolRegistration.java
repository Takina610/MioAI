package com.mio.ai.superagent.tools;

import com.mio.ai.superagent.tools.CSAppTools.*;
import com.mio.ai.superagent.tools.CommonTools.*;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author: Takina
 * @date: 2026/3/31 17:13
 * @description: 集中的工具注册类
 */

@Configuration
public class ToolRegistration {
    @Bean(name = "csAppTools")
    public ToolCallback[] csAppTools(EventReviewTool eventReviewTool,
                                   GrenadeGuideTool grenadeGuideTool,
                                   MapTacticTool mapTacticTool,
                                   PlayerStatsTool playerStatsTool,
                                   TeamAnalyzeTool teamAnalyzeTool
    ) {

        return ToolCallbacks.from(
                eventReviewTool,
                grenadeGuideTool,
                mapTacticTool,
                playerStatsTool,
                teamAnalyzeTool
        );
    }

    @Bean(name = "commonTools")
    public ToolCallback[] commonTools(FileOperationTool fileOperationTool,
                                      PDFGenerationTool pdfGenerationTool,
                                      ResourceDownloadTool resourceDownloadTool,
                                      TerminalOperationTool terminalOperationTool,
                                      TerminateTool terminateTool,
                                      WebScrapingTool webScrapingTool,
                                      WebSearchTool webSearchTool) {
        return ToolCallbacks.from(
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminalOperationTool,
                terminateTool,
                webScrapingTool,
                webSearchTool
        );
    }
}
