package com.mio.ai.bot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.bot.service.image.ImageResult;
import com.mio.ai.bot.service.image.ImageSearchService;
import com.mio.ai.framework.zagent.tools.ToolContext;
import com.mio.ai.framework.zagent.tools.ToolEntry;

import java.util.List;

/**
 * image_search 工具（图文混排的检索入口，MioBot 主轮次注册）：
 * 模型判断回答需要真实图片时调用，取回规范化候选列表（含 URL/来源/作者/授权），
 * 再以 render_ui 图片块组织展示。工具结果即登记来源——
 * 回执中出现的 URL 才是 render_ui 可引用的 URL（防伪造约束在 UiBlockValidator）。
 */
final class ImageSearchTool {

    private static final int GENERAL_LIMIT = 8;
    private static final int ANIME_LIMIT = 6;

    private static final String DESCRIPTION = """
            Search the web for real images (photos, artwork, character portraits) to illustrate your answer. \
            Returns numbered candidates with imageUrl / thumbnailUrl / source page / author / license. \
            Use it when the answer covers a concrete visual entity: anime or game characters and works, real people, \
            landmarks, travel destinations, cities, products, hardware. Use category="anime" for anime/manga \
            characters or works (it returns both the named characters and the work's main cast). \
            The query must be a SHORT entity name in English or romaji (e.g. "One Piece", "Rem", \
            "Fushimi Inari Shrine") — never a sentence or description. \
            NOT for abstract concepts, code, math, translations or any question where images add nothing. \
            The returned URLs must be used VERBATIM in render_ui image blocks (image / image_list / \
            image_gallery / figure); any other URL is rejected. If this tool fails or returns nothing, \
            just answer without images.""";

    private static final String SCHEMA = """
            {"type":"object","properties":{
            "query":{"type":"string","minLength":2,"description":"Image search query (English or romaji works best), e.g. a character name, landmark or product name"},
            "category":{"type":"string","enum":["general","anime"],"description":"anime for anime/manga characters or works; general (default) for everything else"}},
            "required":["query"]}""";

    private ImageSearchTool() {
    }

    static ToolEntry entry(ImageSearchService service) {
        ToolEntry.StringHandler handler = (input, ctx) -> execute(input, service, ctx);
        return ToolEntry.ofReadOnly("image_search", DESCRIPTION, SCHEMA, handler);
    }

    private static String execute(JsonNode input, ImageSearchService service, ToolContext ctx) {
        String query = input == null ? "" : input.path("query").asText("").trim();
        if (query.length() < 2) {
            return "<tool_use_error>query is required (at least 2 characters).</tool_use_error>";
        }
        String category = "anime".equals(input.path("category").asText("")) ? "anime" : "general";
        List<ImageResult> results;
        try {
            results = service.search(ctx.chatId, query, category,
                    "anime".equals(category) ? ANIME_LIMIT : GENERAL_LIMIT);
        } catch (Exception e) {
            return "Image search is unavailable right now (network error or timeout). "
                    + "Answer without images as usual.";
        }
        if (results.isEmpty()) {
            return "No images found for \"" + query + "\". Try once more with a shorter query "
                    + "(just the work/character/place name, English or romaji); if it still returns nothing, "
                    + "answer without images and do not invent image URLs.";
        }
        StringBuilder sb = new StringBuilder("Image candidates for \"").append(query).append("\":\n");
        for (int i = 0; i < results.size(); i++) {
            ImageResult r = results.get(i);
            sb.append('[').append(i + 1).append("] ").append(r.title() == null ? "(untitled)" : r.title()).append('\n');
            sb.append("    image: ").append(r.imageUrl()).append('\n');
            if (r.thumbnailUrl() != null && !r.thumbnailUrl().equals(r.imageUrl())) {
                sb.append("    thumb: ").append(r.thumbnailUrl()).append('\n');
            }
            if (r.sourceUrl() != null) {
                sb.append("    page: ").append(r.sourceUrl()).append('\n');
            }
            sb.append("    author: ").append(r.author() == null ? "-" : r.author())
                    .append(" | license: ").append(r.license() == null ? "-" : r.license())
                    .append(" | via ").append(r.sourceName()).append('\n');
            if (r.desc() != null) {
                sb.append("    desc: ").append(r.desc()).append('\n');
            }
        }
        sb.append("Use these URLs verbatim in render_ui image blocks; never modify or invent URLs. ")
                .append("Pick only images that genuinely match the subject.");
        return sb.toString();
    }
}
