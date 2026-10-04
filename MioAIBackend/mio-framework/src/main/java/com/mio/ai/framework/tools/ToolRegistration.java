package com.mio.ai.framework.tools;

import com.mio.ai.framework.tools.CommonTools.*;
import com.mio.ai.framework.tools.sandbox.SandboxTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/31 17:13
 * @description: 集中的工具注册类（MioBot 内置工具池）。
 * 当前时间不走工具，由 MioBot 直接注入系统提示词的环境信息块。
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
                                      ImageSearchTool imageSearchTool,
                                      BangumiSearchTool bangumiSearchTool,
                                      CalculatorTool calculatorTool,
                                      ObjectProvider<SandboxTool> sandboxTool
    ) {
        List<Object> tools = new ArrayList<>(List.of(
                fileOperationTool,
                pdfGenerationTool,
                resourceDownloadTool,
                terminalOperationTool,
                webScrapingTool,
                webSearchTool,
                imageSearchTool,
                bangumiSearchTool,
                calculatorTool
        ));
        // 沙箱工具按配置条件注册（mio.ai.sandbox.enabled=true 才存在）
        SandboxTool sandbox = sandboxTool.getIfAvailable();
        if (sandbox != null) {
            tools.add(sandbox);
        }
        return ToolCallbacks.from(tools.toArray());
    }
}
