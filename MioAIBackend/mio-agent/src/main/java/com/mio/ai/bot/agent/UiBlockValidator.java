package com.mio.ai.bot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.bot.service.image.ImageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * UI 块语义校验（render_ui 的逐块白名单）：按 kind 分发，
 * 返回可直接进展示块的有序负载。非法块抛 IllegalArgumentException，
 * 由 UiRenderTool 捕获后丢弃并在回执中说明（模型自纠重调）。
 * <p>基础信息类（cards/chart/table）在本类；流程交互类与图片类分别在
 * {@link UiExtendedBlockValidator} / {@link UiImageBlockValidator}。
 */
final class UiBlockValidator {

    private static final int MAX_CARDS = 6;
    private static final int MAX_SERIES = 4;
    private static final int MAX_POINTS = 24;
    private static final int MAX_COLUMNS = 8;

    /** 文本字段防滥用截断（title/value/desc/cell 等） */
    static final int TEXT_CAP = 200;

    static final Set<String> CHART_TYPES = Set.of("bar", "line", "pie", "scatter");
    static final Set<String> TRENDS = Set.of("up", "down", "flat");

    private UiBlockValidator() {
    }

    /** imageResolver：本会话图片资产登记簿（url→image_search 结果元数据，未登记返回 null） */
    static Map<String, Object> validateBlock(JsonNode raw, Function<String, ImageResult> imageResolver) {
        if (raw == null || !raw.isObject()) {
            throw new IllegalArgumentException("block must be an object");
        }
        String kind = text(raw, "kind");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("kind", kind);
        String title = text(raw, "title");
        if (title != null) {
            block.put("title", title);
        }
        switch (kind) {
            case "cards" -> block.put("cards", validateCards(raw.get("cards")));
            case "chart" -> block.putAll(validateChart(raw));
            case "table" -> block.putAll(UiExtendedBlockValidator.validateTable(raw));
            case "tasks" -> block.put("items", UiExtendedBlockValidator.validateTasks(raw.get("items")));
            case "form" -> block.putAll(UiExtendedBlockValidator.validateForm(raw));
            case "actions" -> block.put("actions", UiExtendedBlockValidator.validateActions(raw.get("actions")));
            case "status" -> block.put("steps", UiExtendedBlockValidator.validateStatus(raw.get("steps")));
            case "timeline" -> block.put("events", UiExtendedBlockValidator.validateTimeline(raw.get("events")));
            case "quote" -> block.putAll(UiExtendedBlockValidator.validateQuote(raw));
            case "metric" -> block.put("items", UiExtendedBlockValidator.validateMetric(raw.get("items")));
            case "choice" -> block.put("options", UiExtendedBlockValidator.validateChoice(raw.get("options")));
            case "image" -> block.putAll(UiImageBlockValidator.validateImage(raw, imageResolver));
            case "image_list" -> block.put("items", UiImageBlockValidator.validateImageList(raw, imageResolver));
            case "image_gallery" -> block.put("images", UiImageBlockValidator.validateGallery(raw, imageResolver));
            case "figure" -> block.putAll(UiImageBlockValidator.validateFigure(raw, imageResolver));
            default -> throw new IllegalArgumentException(
                    "unsupported kind '" + kind + "' (supported: cards, chart, table, tasks, form, actions, status, "
                            + "timeline, quote, metric, choice, image, image_list, image_gallery, figure)");
        }
        return block;
    }

    static List<Map<String, Object>> validateCards(JsonNode raw) {
        List<Map<String, Object>> cards = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (cards.size() >= MAX_CARDS || !item.isObject()) {
                    continue;
                }
                String cardTitle = text(item, "title");
                if (cardTitle == null) {
                    continue;
                }
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("title", cardTitle);
                String value = text(item, "value");
                if (value != null) {
                    card.put("value", value);
                }
                String desc = text(item, "desc");
                if (desc != null) {
                    card.put("desc", desc);
                }
                String trend = text(item, "trend");
                if (trend != null) {
                    card.put("trend", TRENDS.contains(trend) ? trend : "flat");
                }
                cards.add(card);
            }
        }
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("cards needs 1-" + MAX_CARDS
                    + " items each with a non-empty title");
        }
        return cards;
    }

    static Map<String, Object> validateChart(JsonNode raw) {
        String chartType = text(raw, "chartType");
        if (chartType == null || !CHART_TYPES.contains(chartType)) {
            throw new IllegalArgumentException("chartType must be one of: bar, line, pie, scatter");
        }
        JsonNode rawSeries = raw.get("series");
        if (rawSeries == null || !rawSeries.isArray() || rawSeries.isEmpty()) {
            throw new IllegalArgumentException("chart needs a non-empty series array");
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (JsonNode item : rawSeries) {
            if (series.size() >= MAX_SERIES || !item.isObject()) {
                continue;
            }
            String name = text(item, "name");
            JsonNode rawData = item.get("data");
            if (name == null || rawData == null || !rawData.isArray() || rawData.isEmpty()) {
                continue;
            }
            List<Double> data = new ArrayList<>();
            for (JsonNode point : rawData) {
                if (data.size() >= MAX_POINTS) {
                    break;
                }
                Double value = asNumber(point);
                if (value == null) {
                    throw new IllegalArgumentException("series '" + name + "' has a non-numeric data point");
                }
                data.add(value);
            }
            if (!data.isEmpty()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", name);
                s.put("data", data);
                series.add(s);
            }
        }
        if (series.isEmpty()) {
            throw new IllegalArgumentException("chart needs at least one series with numeric data");
        }
        // categories 缺失/不足时按序号补齐（饼图取首系列配对）
        List<String> categories = new ArrayList<>();
        if (raw.get("categories") != null && raw.get("categories").isArray()) {
            for (JsonNode c : raw.get("categories")) {
                categories.add(cap(String.valueOf(c.isTextual() ? c.asText() : c)));
            }
        }
        int points = series.get(0).get("data") instanceof List<?> list ? list.size() : 0;
        while (categories.size() < points) {
            categories.add(String.valueOf(categories.size() + 1));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("chartType", chartType);
        fields.put("categories", categories);
        fields.put("series", series);
        return fields;
    }

    // ---------- 共享取值助手（各 kind 校验器共用） ----------

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = cap(value.isTextual() ? value.asText() : String.valueOf(value));
        return s.isBlank() ? null : s;
    }

    static String cap(String s) {
        return s.length() > TEXT_CAP ? s.substring(0, TEXT_CAP) + "…" : s;
    }

    static Double asNumber(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
