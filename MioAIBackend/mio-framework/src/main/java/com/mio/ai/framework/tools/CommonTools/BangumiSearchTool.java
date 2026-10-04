package com.mio.ai.framework.tools.CommonTools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author: Takina
 * @date: 2026/10/4
 * @description: Bangumi（bgm.tv）条目搜索工具：动画/漫画/游戏/小说/音乐的结构化资料
 * （中日文名、年份、评分、集数、简介），比网页搜索更准，是作品类问题的首选数据源。
 * 搜索接口不带评分，取前几条后再用 v0 详情接口逐条补全（静默降级）。
 */
@Component
public class BangumiSearchTool {

    private static final String SEARCH_URL = "https://api.bgm.tv/search/subject/";

    private static final String SUBJECT_URL = "https://api.bgm.tv/v0/subjects/";

    private static final int MAX_RESULTS = 5;

    private static final int SUMMARY_MAX_CHARS = 200;

    private static final String USER_AGENT = "mioai/miobot (graduation-project)";

    @Tool(description = "搜索 Bangumi 条目库，查询动画/漫画/游戏/小说/音乐等作品的资料："
            + "中日文名、播出/发售日期、评分（分数与人数）、集数、简介与条目链接。"
            + "介绍作品、查询作品信息时优先用这个工具；主系列某季搜不到时可用「作品名 第一季/第二季/剧场版」等关键词分别查询")
    public String searchBangumi(@ToolParam(description = "作品名称关键词，日文原名或中文译名均可") String keyword) {
        try {
            String keywordTrimmed = keyword == null ? "" : keyword.trim();
            String url = SEARCH_URL + URLEncoder.encode(keywordTrimmed, StandardCharsets.UTF_8)
                    + "?max_results=25";
            String response = httpGet(url);
            return parseResults(response);
        } catch (Exception e) {
            return "Bangumi 查询出错: " + e.getMessage();
        }
    }

    private String parseResults(String response) {
        JSONObject root = JSONUtil.parseObj(response);
        // bgm.tv 的搜索结果数组在 list 字段，results 是命中数量
        JSONArray list = root.getJSONArray("list");
        if (list == null || list.isEmpty()) {
            return "没有搜到相关条目，可换个关键词（如日文原名、或「作品名 剧场版/第二季」）再试";
        }
        List<JSONObject> subjects = new ArrayList<>();
        for (Object item : list) {
            subjects.add((JSONObject) item);
        }
        // 只对前若干条补全详情（控制请求量），再按评分人数排出主条目
        List<JSONObject> candidates = subjects.subList(0, Math.min(10, subjects.size()));
        List<JSONObject> details = new ArrayList<>();
        for (JSONObject subject : candidates) {
            details.add(fetchDetail(subject.getLong("id")));
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator
                .comparingLong((Integer i) -> ratingTotal(details.get(i))).reversed()
                .thenComparingInt(i -> StrUtil.isNotBlank(candidates.get(i).getStr("date")) ? 0 : 1));

        StringJoiner sj = new StringJoiner("\n\n");
        int count = 0;
        for (int i : order) {
            if (count >= MAX_RESULTS) {
                break;
            }
            sj.add(formatSubject(candidates.get(i), details.get(i)));
            count++;
        }
        return sj.toString();
    }

    private long ratingTotal(JSONObject detail) {
        JSONObject rating = detail != null ? detail.getJSONObject("rating") : null;
        return rating != null && rating.getLong("total") != null ? rating.getLong("total") : 0L;
    }

    /** 条目行：中文名 | 日文名 | 日期 | 评分(人数) | 集数 + 简介 + 链接；详情补全失败时用搜索字段兜底 */
    private String formatSubject(JSONObject subject, JSONObject detail) {
        String displayName = StrUtil.blankToDefault(subject.getStr("name_cn"), subject.getStr("name"));
        StringJoiner line = new StringJoiner(" | ");
        line.add(displayName);
        if (StrUtil.isNotBlank(subject.getStr("name")) && !displayName.equals(subject.getStr("name"))) {
            line.add(subject.getStr("name"));
        }
        String date = detail != null && StrUtil.isNotBlank(detail.getStr("date"))
                ? detail.getStr("date") : subject.getStr("date");
        if (StrUtil.isNotBlank(date)) {
            line.add(date);
        }
        JSONObject rating = detail != null ? detail.getJSONObject("rating") : null;
        if (rating != null && rating.get("score") != null) {
            line.add("评分 " + rating.getStr("score")
                    + (rating.getLong("total") != null ? "（" + rating.getLong("total") + " 人）" : ""));
        }
        Integer eps = detail != null && detail.getInt("eps") != null ? detail.getInt("eps") : subject.getInt("eps");
        if (eps != null && eps > 0) {
            line.add(eps + " 集");
        }
        StringBuilder sb = new StringBuilder("- ").append(line);
        String summary = detail != null && StrUtil.isNotBlank(detail.getStr("summary"))
                ? detail.getStr("summary") : subject.getStr("summary");
        if (StrUtil.isNotBlank(summary)) {
            sb.append("\n  ").append(StrUtil.brief(summary, SUMMARY_MAX_CHARS));
        }
        sb.append("\n  ").append("https://bgm.tv/subject/").append(subject.getLong("id"));
        return sb.toString();
    }

    /** v0 详情接口补全评分/集数/简介；失败返回 null（搜索字段兜底） */
    private JSONObject fetchDetail(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return JSONUtil.parseObj(httpGet(SUBJECT_URL + id));
        } catch (Exception e) {
            return null;
        }
    }

    private String httpGet(String url) {
        return HttpUtil.createGet(url)
                .header("User-Agent", USER_AGENT)
                .timeout(8000)
                .execute()
                .body();
    }
}
