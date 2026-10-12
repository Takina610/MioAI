package com.mio.ai.bot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.bot.service.image.ImageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.mio.ai.bot.agent.UiBlockValidator.text;

/**
 * 图片类块校验（image / image_list / image_gallery / figure）：
 * 核心安全约束——每个 imageUrl 必须是本会话 image_search 工具结果登记过的
 * https URL（防模型凭空编造/篡改 URL 绕过图片检索）。命中的 url 自动回填
 * 来源/作者/授权/尺寸元数据（登记簿真实数据，模型只负责挑选与文案，
 * 不必也不应手抄元数据）；未登记的缩略图静默丢弃（可降级用原图）。
 */
final class UiImageBlockValidator {

    private static final int MAX_LIST_ITEMS = 8;
    private static final int MAX_GALLERY_IMAGES = 12;

    private static final Set<String> RATIOS = Set.of("auto", "16/9", "4/3", "1/1", "3/4");
    private static final Set<String> FITS = Set.of("cover", "contain");

    private static final String URL_RULE = "image urls must come from this chat's image_search results "
            + "(https only) — call image_search and copy the returned image:/thumb: URLs verbatim";

    private UiImageBlockValidator() {
    }

    /** kind=image：单张图 + 展示配置 */
    static Map<String, Object> validateImage(JsonNode raw, Function<String, ImageResult> imageResolver) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("image", requireImageItem(raw.get("image"), imageResolver));
        putDisplayConfig(raw, fields);
        return fields;
    }

    /** kind=figure：图 + 说明文字（figure 的意义就在 caption，必填） */
    static Map<String, Object> validateFigure(JsonNode raw, Function<String, ImageResult> imageResolver) {
        JsonNode imageNode = raw.get("image");
        if (imageNode == null || !imageNode.isObject()) {
            throw new IllegalArgumentException("figure needs an image object with a url field. " + URL_RULE);
        }
        String caption = text(raw, "caption");
        if (caption == null) {
            throw new IllegalArgumentException("figure needs a non-empty caption explaining the image");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("image", imageMeta(imageNode, requireUrl(imageNode, imageResolver)));
        fields.put("caption", caption);
        putDisplayConfig(raw, fields);
        return fields;
    }

    /** kind=image_list：图文行（多实体：角色名单/产品清单/搜索结果） */
    static List<Map<String, Object>> validateImageList(JsonNode raw, Function<String, ImageResult> imageResolver) {
        JsonNode rawItems = raw.get("items");
        if (rawItems == null || !rawItems.isArray() || rawItems.isEmpty()) {
            throw new IllegalArgumentException("image_list needs 1-" + MAX_LIST_ITEMS
                    + " items each with an image url. " + URL_RULE);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode item : rawItems) {
            if (items.size() >= MAX_LIST_ITEMS || !item.isObject()) {
                continue;
            }
            items.add(requireImageItem(item, imageResolver));
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("image_list has no valid item. " + URL_RULE);
        }
        return items;
    }

    /** kind=image_gallery：图集网格（景点/作品图集） */
    static List<Map<String, Object>> validateGallery(JsonNode raw, Function<String, ImageResult> imageResolver) {
        JsonNode rawImages = raw.get("images");
        if (rawImages == null || !rawImages.isArray() || rawImages.isEmpty()) {
            throw new IllegalArgumentException("image_gallery needs 1-" + MAX_GALLERY_IMAGES
                    + " images each with a url. " + URL_RULE);
        }
        List<Map<String, Object>> images = new ArrayList<>();
        for (JsonNode item : rawImages) {
            if (images.size() >= MAX_GALLERY_IMAGES || !item.isObject()) {
                continue;
            }
            images.add(requireImageItem(item, imageResolver));
        }
        if (images.isEmpty()) {
            throw new IllegalArgumentException("image_gallery has no valid image. " + URL_RULE);
        }
        return images;
    }

    // ---------- 共享 ----------

    /** 块载体里的图片对象（url 必填） */
    private static Map<String, Object> requireImageItem(JsonNode item, Function<String, ImageResult> imageResolver) {
        JsonNode node = item != null && item.isObject() ? item : null;
        if (node == null) {
            throw new IllegalArgumentException("image object with a url field is required. " + URL_RULE);
        }
        return imageMeta(node, requireUrl(node, imageResolver));
    }

    /** 登记簿核对：https + 本会话 image_search 真实结果，命中返回完整元数据 */
    private static ImageResult requireUrl(JsonNode item, Function<String, ImageResult> imageResolver) {
        String url = text(item, "url");
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException(URL_RULE);
        }
        ImageResult asset = imageResolver.apply(url);
        if (asset == null) {
            throw new IllegalArgumentException("image url was not returned by image_search for this chat. " + URL_RULE);
        }
        return asset;
    }

    /**
     * 模型字段优先（title/alt/caption/desc 是它写的文案），登记簿回填缺失的
     * 真实元数据（sourceUrl/sourceName/author/license/width/height）——
     * 来源信息展示不依赖模型抄写，照实透传不推断。
     */
    private static Map<String, Object> imageMeta(JsonNode item, ImageResult asset) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", asset.imageUrl());
        String thumbnail = text(item, "thumbnailUrl");
        if (thumbnail != null && thumbnail.startsWith("https://")) {
            image.put("thumbnailUrl", thumbnail);
        } else if (asset.thumbnailUrl() != null) {
            image.put("thumbnailUrl", asset.thumbnailUrl());
        }
        for (String field : List.of("title", "alt", "caption", "desc")) {
            String value = text(item, field);
            if (value != null) {
                image.put(field, value);
            }
        }
        String modelUrl = text(item, "sourceUrl");
        if (modelUrl != null && modelUrl.startsWith("https://")) {
            image.put("sourceUrl", modelUrl);
        } else if (asset.sourceUrl() != null && asset.sourceUrl().startsWith("https://")) {
            image.put("sourceUrl", asset.sourceUrl());
        }
        for (String field : List.of("sourceName", "author", "license")) {
            String value = text(item, field);
            if (value != null) {
                image.put(field, value);
                continue;
            }
            String fromAsset = switch (field) {
                case "sourceName" -> asset.sourceName();
                case "author" -> asset.author();
                default -> asset.license();
            };
            if (fromAsset != null) {
                image.put(field, fromAsset);
            }
        }
        Integer width = intOrNull(item.get("width"));
        Integer height = intOrNull(item.get("height"));
        if (width == null) {
            width = asset.width();
        }
        if (height == null) {
            height = asset.height();
        }
        if (width != null) {
            image.put("width", width);
        }
        if (height != null) {
            image.put("height", height);
        }
        return image;
    }

    private static void putDisplayConfig(JsonNode raw, Map<String, Object> fields) {
        String ratio = text(raw, "ratio");
        if (ratio != null && RATIOS.contains(ratio)) {
            fields.put("ratio", ratio);
        }
        String fit = text(raw, "fit");
        if (fit != null && FITS.contains(fit)) {
            fields.put("fit", fit);
        }
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        int value = node.asInt();
        return value > 0 ? value : null;
    }
}
