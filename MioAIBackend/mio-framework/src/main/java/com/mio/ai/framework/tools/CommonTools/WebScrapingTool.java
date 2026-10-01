package com.mio.ai.framework.tools.CommonTools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 18:48
 * @description: 网页抓取工具
 */

@Component
public class WebScrapingTool {

    @Tool(description = "抓取网页内容")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页URL") String url) {
        try {
            Document document = Jsoup.connect(url).get();
            return document.html();
        } catch (Exception e) {
            return "抓取网页错误: " + e.getMessage();
        }
    }
}
