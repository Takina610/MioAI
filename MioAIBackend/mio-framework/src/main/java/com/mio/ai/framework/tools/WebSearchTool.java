package com.mio.ai.framework.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/3/31 18:49
 * @description: 网页搜索工具。
 * 配置了 SearchAPI 密钥时走 SearchAPI（engine=baidu）；未配置或调用失败时
 * 自动降级为 DuckDuckGo HTML 端点（免密钥），保证搜索能力开箱可用。
 */
@Component
public class WebSearchTool {

    private static final int MAX_RESULTS = 5;

    // SearchAPI 的搜索接口地址
    @Value("${web.search.url}")
    private String SEARCH_API_URL;

    // SearchAPI 的 apiKey
    @Value("${web.search.api-key:}")
    private String apiKey;

    @Tool(description = "联网搜索信息，返回前几条结果的标题、摘要与链接")
    public String searchWeb(
            @ToolParam(description = "搜索查询关键词") String query) {
        if (StrUtil.isNotBlank(apiKey)) {
            try {
                String searched = searchViaApi(query);
                if (searched != null) {
                    return searched;
                }
            } catch (Exception ignored) {
                // 走降级通道
            }
        }
        return searchViaDuckDuckGo(query);
    }

    /**
     * SearchAPI 通道；返回 null 表示本次不可用（交由调用方降级）
     */
    private String searchViaApi(String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        String response = HttpUtil.get(SEARCH_API_URL, paramMap);

        JSONObject jsonObject = JSONUtil.parseObj(response);
        if (jsonObject.get("error") != null) {
            // 把服务端错误透出给模型，避免无意义重试
            return "搜索服务返回错误: " + jsonObject.get("error");
        }
        JSONArray organicResults = jsonObject.getJSONArray("organic_results");
        if (organicResults == null || organicResults.isEmpty()) {
            return null;
        }
        List<Object> topResults = organicResults.subList(0, Math.min(MAX_RESULTS, organicResults.size()));
        return topResults.stream().map(String::valueOf).collect(Collectors.joining(",\n"));
    }

    /**
     * DuckDuckGo HTML 通道（免密钥兜底），用 jsoup 解析结果链接与摘要
     */
    private String searchViaDuckDuckGo(String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("kl", "cn-zh");
        String html = HttpUtil.get("https://html.duckduckgo.com/html/", paramMap);
        Document document = Jsoup.parse(html);
        Elements results = document.select(".result");
        if (results.isEmpty()) {
            return "没有搜索到相关结果，请换个关键词，或用 fetchUrl 直接抓取已知网址";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Element result : results) {
            if (count >= MAX_RESULTS) {
                break;
            }
            Element link = result.selectFirst(".result__a");
            Element snippet = result.selectFirst(".result__snippet");
            if (link == null) {
                continue;
            }
            if (count > 0) {
                sb.append(",\n");
            }
            sb.append("{\"title\": \"").append(escape(link.text()))
                    .append("\", \"snippet\": \"").append(escape(snippet != null ? snippet.text() : ""))
                    .append("\", \"url\": \"").append(escape(link.absUrl("href")))
                    .append("\"}");
            count++;
        }
        if (count == 0) {
            return "没有搜索到相关结果，请换个关键词，或用 fetchUrl 直接抓取已知网址";
        }
        return sb.toString();
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
