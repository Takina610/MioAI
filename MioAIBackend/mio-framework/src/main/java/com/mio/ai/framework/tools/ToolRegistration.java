package com.mio.ai.framework.tools;

import com.mio.ai.framework.tools.pdf.PDFGenerationTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内置工具池：全部为通用原语（参考 zcode/opencode 的工具哲学）。
 * <p>不给特定内容做定制工具——能力面 = 通用原语 × 模型组合：
 * bash（真实 Linux 沙箱）、文件读/写/精确编辑、文件名/内容搜索、
 * 联网搜索、网页抓取、任务清单、PDF 导出（用户产物出口）。
 * <p>沙箱原语（SandboxShell/File/SearchTool）由 SandboxConfig 条件装配，
 * 未启用沙箱时这些 Bean 不存在，工具池自动缩为纯 web + 清单 + PDF。
 */
@Configuration(enforceUniqueMethods = false)
public class ToolRegistration {

    @Bean(name = "commonTools")
    public ToolCallback[] commonTools(WebSearchTool webSearchTool,
                                      WebFetchTool webFetchTool,
                                      PDFGenerationTool pdfGenerationTool,
                                      ObjectProvider<SandboxShellTool> shell,
                                      ObjectProvider<SandboxFileTool> file,
                                      ObjectProvider<SandboxSearchTool> search
    ) {
        return ToolCallbacks.from(
                webSearchTool,
                webFetchTool,
                pdfGenerationTool,
                shell.getIfAvailable(),
                file.getIfAvailable(),
                search.getIfAvailable()
        );
    }
}
