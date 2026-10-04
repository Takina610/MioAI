package com.mio.ai.framework.zagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.WEB_SEARCH;

/**
 * WebSearch 工具（zcode handlers/websearch.ts 的对位移植）：
 * 描述注入当前月份、结果块 + Sources 纪律、allowed/blocked_domains 过滤；
 * 后端复用 SearchAPI（配 key 时）/ DuckDuckGo（免密钥兜底）双通道。
 */
final class WebSearchTool {

    private static final int MAX_RESULTS = 8;

    private final String searchApiUrl;
    private final String searchApiKey;

    WebSearchTool(String searchApiUrl, String searchApiKey) {
        this.searchApiUrl = searchApiUrl;
        this.searchApiKey = searchApiKey;
    }

    record Result(String title, String url) {
    }

    ToolEntry entry() {
        String month = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        String description = WEB_SEARCH.formatted(month);
        String schema = """
                {"type":"object","properties":{
                "query":{"type":"string","minLength":2,"description":"The search query to use"},
                "allowed_domains":{"type":"array","items":{"type":"string"},"description":"Only include search results from these domains"},
                "blocked_domains":{"type":"array","items":{"type":"string"},"description":"Never include search results from these domains"}},
                "required":["query"]}""";
        return ToolEntry.ofReadOnly("WebSearch", description, schema, this::execute);
    }

    String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        String query = Args.str(input, "query");
        if (query == null || query.length() < 2) {
            throw new ToolUseFailure(1, "Search query is required.");
        }
        List<String> allowed = stringList(input, "allowed_domains");
        List<String> blocked = stringList(input, "blocked_domains");
        if (!allowed.isEmpty() && !blocked.isEmpty()) {
            throw new ToolUseFailure(2, "allowed_domains and blocked_domains cannot both be specified.");
        }
        List<Result> results = StrUtil.isNotBlank(searchApiKey) ? searchViaApi(query) : List.of();
        if (results.isEmpty()) {
            results = searchViaDuckDuckGo(query);
        }
        List<Result> filtered = new ArrayList<>();
        for (Result result : results) {
            String domain = domainOf(result.url());
            if (!allowed.isEmpty() && allowed.stream().noneMatch(domain::contains)) {
                continue;
            }
            if (!blocked.isEmpty() && blocked.stream().anyMatch(domain::contains)) {
                continue;
            }
            filtered.add(result);
            if (filtered.size() >= MAX_RESULTS) {
                break;
            }
        }
        if (filtered.isEmpty()) {
            return "No search results found for: " + query;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filtered.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(filtered.get(i).title())
                    .append('\n').append(filtered.get(i).url()).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    private List<Result> searchViaApi(String query) {
        try {
            Map<String, Object> params = Map.of(
                    "q", query, "api_key", searchApiKey == null ? "" : searchApiKey, "engine", "baidu");
            String response = HttpUtil.get(searchApiUrl, params);
            JSONObject json = JSONUtil.parseObj(response);
            if (json.get("error") != null) {
                return List.of();
            }
            JSONArray organic = json.getJSONArray("organic_results");
            if (organic == null || organic.isEmpty()) {
                return List.of();
            }
            List<Result> results = new ArrayList<>();
            for (Object item : organic) {
                JSONObject obj = (JSONObject) item;
                String title = obj.getStr("title", "");
                String url = obj.getStr("link", "");
                if (StrUtil.isNotBlank(url)) {
                    results.add(new Result(StrUtil.blankToDefault(title, url), url));
                }
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Result> searchViaDuckDuckGo(String query) {
        List<Result> results = new ArrayList<>();
        try {
            Map<String, Object> params = Map.of("q", query, "kl", "cn-zh");
            String html = HttpUtil.get("https://html.duckduckgo.com/html/", params);
            Document document = Jsoup.parse(html);
            Elements rows = document.select(".result");
            for (Element row : rows) {
                Element link = row.selectFirst(".result__a");
                if (link == null) {
                    continue;
                }
                results.add(new Result(link.text(), link.absUrl("href")));
                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }
        } catch (Exception ignored) {
            // 兜底通道失败：按无结果处理
        }
        return results;
    }

    private static List<String> stringList(com.fasterxml.jackson.databind.JsonNode input, String field) {
        List<String> values = new ArrayList<>();
        if (input != null && input.has(field) && input.get(field).isArray()) {
            input.get(field).forEach(node -> {
                if (node != null && !node.asText().isBlank()) {
                    values.add(node.asText());
                }
            });
        }
        return values;
    }

    private static String domainOf(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
