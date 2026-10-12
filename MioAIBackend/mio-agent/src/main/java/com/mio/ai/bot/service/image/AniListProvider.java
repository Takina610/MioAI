package com.mio.ai.bot.service.image;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AniList 动漫数据（https://graphql.anilist.co，免 key，实测 2026-10-12）。
 * <p>双路合一查询：①角色名搜索（用户问"某个角色"）；②作品 CAST（用户问"某部作品的主要角色"，
 * Media.characters 按戏份排序）——单一 name-search 对整句/作品名查询必然空结果，双路覆盖两种问法。
 * 平台不提供授权字段——license/author 留空，来源统一标注 AniList，不虚构授权信息。
 */
class AniListProvider implements ImageSearchProvider {

    private static final int TIMEOUT_MS = 8_000;
    private static final int DESC_CAP = 300;
    private static final int NAME_SEARCH_LIMIT = 4;
    private static final int CAST_LIMIT = 8;

    /** 双根字段查询（Page 单子字段限制已实测：角色名搜索与作品 CAST 分置 Page/Media） */
    private static final String QUERY = """
            query($s:String){Page(perPage:%d){characters(search:$s){id name{full native} image{large} \
            description(asHtml:false) media(perPage:1){nodes{title{romaji}}}}}\
            Media(search:$s,type:ANIME){id title{romaji} coverImage{extraLarge} \
            characters(perPage:%d,sort:ROLE){nodes{id name{full} image{large} description(asHtml:false)}}}}"""
            .formatted(NAME_SEARCH_LIMIT, CAST_LIMIT);

    private final String endpoint;

    AniListProvider(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String name() {
        return "AniList";
    }

    @Override
    public List<ImageResult> search(String query, int limit) {
        JSONObject payload = new JSONObject()
                .set("query", QUERY)
                .set("variables", new JSONObject().set("s", query));
        String body = HttpRequest.post(endpoint)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(payload.toString())
                .timeout(TIMEOUT_MS)
                .execute()
                .body();
        JSONObject data = JSONUtil.parseObj(body).getJSONObject("data");
        if (data == null) {
            return List.of();
        }
        // 角色名精确命中优先（问的是"某角色"），作品 CAST 随后（问的是"某作品的主要角色"）
        Set<String> seenIds = new LinkedHashSet<>();
        List<ImageResult> out = new ArrayList<>();
        appendCharacterHits(charactersOf(data.getJSONObject("Page")), query, limit, seenIds, out);
        appendCastHits(data.getJSONObject("Media"), limit, seenIds, out);
        appendWorkCover(data.getJSONObject("Media"), limit, out);
        return out;
    }

    private static JSONArray charactersOf(JSONObject page) {
        return page == null ? null : page.getJSONArray("characters");
    }

    private static void appendCharacterHits(JSONArray characters, String query, int limit,
                                            Set<String> seenIds, List<ImageResult> out) {
        if (characters == null) {
            return;
        }
        for (Object item : characters) {
            JSONObject ch = (JSONObject) item;
            String from = firstMediaTitle(ch);
            if (addCharacter(ch, from, limit, seenIds, out) && out.size() >= limit) {
                return;
            }
        }
    }

    private static void appendCastHits(JSONObject media, int limit, Set<String> seenIds, List<ImageResult> out) {
        if (media == null) {
            return;
        }
        JSONObject connection = media.getJSONObject("characters");
        JSONArray nodes = connection == null ? null : connection.getJSONArray("nodes");
        if (nodes == null) {
            return;
        }
        JSONObject title = media.getJSONObject("title");
        String work = title == null ? null : title.getStr("romaji");
        for (Object item : nodes) {
            addCharacter((JSONObject) item, work, limit, seenIds, out);
            if (out.size() >= limit) {
                return;
            }
        }
    }

    /** 返回是否达到上限（null-safe） */
    private static boolean addCharacter(JSONObject ch, String work, int limit,
                                        Set<String> seenIds, List<ImageResult> out) {
        if (out.size() >= limit || ch == null) {
            return true;
        }
        String id = ch.getStr("id");
        if (id == null || !seenIds.add("al:" + id)) {
            return false;
        }
        JSONObject image = ch.getJSONObject("image");
        String imageUrl = image == null ? null : image.getStr("large");
        if (imageUrl == null || !imageUrl.startsWith("https://")) {
            return false;
        }
        JSONObject name = ch.getJSONObject("name");
        String full = name == null ? null : name.getStr("full");
        String title = full == null ? null : full + (work == null ? "" : " · " + work);
        out.add(new ImageResult(
                "al:" + id,
                imageUrl,
                imageUrl,
                "https://anilist.co/character/" + id,
                "AniList",
                title,
                sanitize(ch.getStr("description")),
                null,
                null,
                null,
                null));
        return false;
    }

    /** 作品封面补充（主视觉，作图集收尾） */
    private static void appendWorkCover(JSONObject media, int limit, List<ImageResult> out) {
        if (media == null || out.size() >= limit) {
            return;
        }
        JSONObject cover = media.getJSONObject("coverImage");
        String coverUrl = cover == null ? null : cover.getStr("extraLarge");
        JSONObject title = media.getJSONObject("title");
        String romaji = title == null ? null : title.getStr("romaji");
        String id = media.getStr("id");
        if (coverUrl == null || !coverUrl.startsWith("https://") || romaji == null || id == null) {
            return;
        }
        out.add(new ImageResult(
                "al:media-" + id,
                coverUrl,
                coverUrl,
                "https://anilist.co/anime/" + id,
                "AniList",
                "《" + romaji + "》作品封面",
                null,
                null,
                null,
                null,
                null));
    }

    /** 角色出自作品名（media.nodes 首个节点的 romaji 标题） */
    private static String firstMediaTitle(JSONObject character) {
        JSONObject media = character.getJSONObject("media");
        JSONArray nodes = media == null ? null : media.getJSONArray("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        JSONObject node = nodes.getJSONObject(0);
        JSONObject title = node == null ? null : node.getJSONObject("title");
        return title == null ? null : title.getStr("romaji");
    }

    /** 简介清洗：去 markdown 链接/强调/HTML 标签，压空白，截断（模型据此写介绍，不原文照搬） */
    static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("__([^_]*)__", "$1")
                .replaceAll("~!.*?!~", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > DESC_CAP ? text.substring(0, DESC_CAP) + "…" : text;
    }
}
