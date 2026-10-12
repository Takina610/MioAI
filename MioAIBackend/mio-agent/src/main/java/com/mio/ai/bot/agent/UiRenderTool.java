package com.mio.ai.bot.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.framework.zagent.tools.ToolEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * render_ui 工具：把模型产出的结构化 UI 块发布进回答流（动态交互式回答的产品侧出口）。
 * <p>模型按 inputSchema 产出 blocks 数组 → 逐块语义校验（非法块丢弃并在回执中说明原因，
 * 模型可自纠重调）→ 校验通过的块经 {@link BotEventChannel#uiBlock} 以 ui_block 事件下发
 * 前端并随展示块落库（校验通过才渲染，无半截 JSON 上屏）。工具调用参数（完整 UI 数据）
 * 由水合器随 tool 块回放进模型上下文——追问时模型知道自己渲染过什么。
 */
final class UiRenderTool {

    private static final int MAX_BLOCKS_PER_CALL = 8;
    private static final int MAX_CARDS = 6;
    private static final int MAX_SERIES = 4;
    private static final int MAX_POINTS = 24;
    private static final int MAX_COLUMNS = 8;
    private static final int MAX_ROWS = 12;
    private static final int MAX_TASKS = 20;
    /** 文本字段防滥用截断（title/value/desc/cell 等） */
    private static final int TEXT_CAP = 200;

    private static final Set<String> CHART_TYPES = Set.of("bar", "line", "pie");
    private static final Set<String> TASK_STATUSES = Set.of("todo", "doing", "done");
    private static final Set<String> TRENDS = Set.of("up", "down", "flat");

    private static final String DESCRIPTION = """
            Render interactive UI blocks (cards, chart, table, task checklist) inline in your chat answer, above your closing text. Use it proactively: whenever your answer contains a multi-stage plan, a comparison of options, or numeric data worth visualizing, render it with this tool instead of Markdown headings and tables. Not for simple Q&A or short answers.""";

    /**
     * inputSchema 只约束 JSON 层（类型/枚举/上限），逐字段语义校验在 {@link #execute} 做——
     * schema 保持扁平（无 oneOf），对弱模型更友好，坏块以回执告知而非整调用失败。
     * 注意：改动结构后务必以 JSON 解析器校验括号平衡——Spring AI 解析失败会静默丢弃参数
     * schema（模型只能瞎猜参数），注册期的 readTree 校验只在日志里提示。
     */
    private static final String SCHEMA = """
            {
            "type":"object",
            "properties":{
            "blocks":{
            "type":"array",
            "minItems":1,
            "maxItems":8,
            "description":"Ordered UI blocks rendered inline in your answer, above your closing text.",
            "items":{
            "type":"object",
            "properties":{
            "kind":{
            "type":"string",
            "enum":["cards","chart","table","tasks"],
            "description":"Block type"
            },
            "title":{
            "type":"string",
            "description":"Optional block title"
            },
            "cards":{
            "type":"array",
            "maxItems":6,
            "description":"kind=cards: stat/info cards",
            "items":{
            "type":"object",
            "properties":{
            "title":{"type":"string"},
            "value":{
            "type":"string",
            "description":"Headline value (number or short stat)"
            },
            "desc":{"type":"string"},
            "trend":{
            "type":"string",
            "enum":["up","down","flat"]
            }
            },
            "required":["title"]
            }
            },
            "chartType":{
            "type":"string",
            "enum":["bar","line","pie"],
            "description":"kind=chart only"
            },
            "categories":{
            "type":"array",
            "items":{"type":"string"},
            "description":"kind=chart: x-axis labels or pie slice names"
            },
            "series":{
            "type":"array",
            "maxItems":4,
            "description":"kind=chart: data series",
            "items":{
            "type":"object",
            "properties":{
            "name":{"type":"string"},
            "data":{
            "type":"array",
            "items":{"type":"number"}
            }
            },
            "required":["name","data"]
            }
            },
            "columns":{
            "type":"array",
            "maxItems":8,
            "items":{"type":"string"},
            "description":"kind=table: column headers"
            },
            "rows":{
            "type":"array",
            "maxItems":12,
            "description":"kind=table: rows of cells (string/number)",
            "items":{"type":"array"}
            },
            "items":{
            "type":"array",
            "maxItems":20,
            "description":"kind=tasks: checklist items",
            "items":{
            "type":"object",
            "properties":{
            "text":{"type":"string"},
            "status":{
            "type":"string",
            "enum":["todo","doing","done"]
            }
            },
            "required":["text"]
            }
            }
            },
            "required":["kind"]
            }
            }
            },
            "required":["blocks"]
            }""";

    private UiRenderTool() {
    }

    /** 注册入口（MioBot 主轮次注册；子代理注册表不含） */
    static ToolEntry entry(BotEventChannel channel) {
        return ToolEntry.of("render_ui", DESCRIPTION, SCHEMA,
                (input, ctx) -> execute(input, channel));
    }

    private static String execute(JsonNode input, BotEventChannel channel) {
        JsonNode rawBlocks = input == null ? null : input.get("blocks");
        if (rawBlocks == null || !rawBlocks.isArray() || rawBlocks.isEmpty()) {
            return "<tool_use_error>blocks must be a non-empty array of UI blocks.</tool_use_error>";
        }
        Map<String, Integer> renderedCounts = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        int index = 0;
        for (JsonNode raw : rawBlocks) {
            index++;
            if (renderedCounts.values().stream().mapToInt(Integer::intValue).sum() >= MAX_BLOCKS_PER_CALL) {
                skipped.add("block[" + index + "] (over " + MAX_BLOCKS_PER_CALL + " blocks per call)");
                continue;
            }
            try {
                Map<String, Object> block = validateBlock(raw);
                renderedCounts.merge(String.valueOf(block.get("kind")), 1, Integer::sum);
                channel.uiBlock(block);
            } catch (IllegalArgumentException e) {
                skipped.add("block[" + index + "] (" + e.getMessage() + ")");
            }
        }
        if (renderedCounts.isEmpty()) {
            return "<tool_use_error>No valid UI block in this call. Reasons: "
                    + String.join("; ", skipped) + " Fix the fields and call render_ui again.</tool_use_error>";
        }
        StringBuilder ack = new StringBuilder("Rendered ").append(renderedCounts.values()
                        .stream().mapToInt(Integer::intValue).sum()).append(" UI block(s): ");
        List<String> parts = new ArrayList<>();
        renderedCounts.forEach((kind, count) -> parts.add(count + " " + kind));
        ack.append(String.join(", ", parts))
                .append(". They appear inline above your closing text; write the closing text next without repeating their content.");
        if (!skipped.isEmpty()) {
            ack.append(" Skipped invalid: ").append(String.join("; ", skipped)).append('.');
        }
        return ack.toString();
    }

    /** 逐块语义校验：返回可直接进展示块的有序负载（kind/title + 各 kind 载荷字段） */
    private static Map<String, Object> validateBlock(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            throw new IllegalArgumentException("block must be an object");
        }
        String kind = text(raw, "kind");
        if (kind == null) {
            throw new IllegalArgumentException("kind is required (cards/chart/table/tasks)");
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
            case "table" -> block.putAll(validateTable(raw));
            case "tasks" -> block.put("items", validateTasks(raw.get("items")));
            default -> throw new IllegalArgumentException(
                    "unsupported kind '" + kind + "' (supported: cards, chart, table, tasks)");
        }
        return block;
    }

    private static List<Map<String, Object>> validateCards(JsonNode raw) {
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

    private static Map<String, Object> validateChart(JsonNode raw) {
        String chartType = text(raw, "chartType");
        if (chartType == null || !CHART_TYPES.contains(chartType)) {
            throw new IllegalArgumentException("chartType must be one of: bar, line, pie");
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

    private static Map<String, Object> validateTable(JsonNode raw) {
        JsonNode rawColumns = raw.get("columns");
        if (rawColumns == null || !rawColumns.isArray() || rawColumns.isEmpty()) {
            throw new IllegalArgumentException("table needs a non-empty columns array");
        }
        List<String> columns = new ArrayList<>();
        for (JsonNode c : rawColumns) {
            if (columns.size() >= MAX_COLUMNS) {
                break;
            }
            columns.add(cap(c.isTextual() ? c.asText() : String.valueOf(c)));
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("table needs at least one column");
        }
        List<List<String>> rows = new ArrayList<>();
        if (raw.get("rows") != null && raw.get("rows").isArray()) {
            for (JsonNode rawRow : raw.get("rows")) {
                if (rows.size() >= MAX_ROWS || !rawRow.isArray()) {
                    continue;
                }
                List<String> row = new ArrayList<>();
                for (JsonNode cell : rawRow) {
                    row.add(cap(cell.isTextual() ? cell.asText() : String.valueOf(cell)));
                }
                // 与列数对齐：不足补空、超长截断
                while (row.size() < columns.size()) {
                    row.add("");
                }
                rows.add(row.subList(0, columns.size()));
            }
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("columns", columns);
        fields.put("rows", rows);
        return fields;
    }

    private static List<Map<String, Object>> validateTasks(JsonNode raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (items.size() >= MAX_TASKS || !item.isObject()) {
                    continue;
                }
                String taskText = text(item, "text");
                if (taskText == null) {
                    continue;
                }
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("text", taskText);
                String status = text(item, "status");
                task.put("status", status != null && TASK_STATUSES.contains(status) ? status : "todo");
                items.add(task);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("tasks needs 1-" + MAX_TASKS + " items each with a non-empty text");
        }
        return items;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String s = cap(value.isTextual() ? value.asText() : String.valueOf(value));
        return s.isBlank() ? null : s;
    }

    private static String cap(String s) {
        return s.length() > TEXT_CAP ? s.substring(0, TEXT_CAP) + "…" : s;
    }

    private static Double asNumber(JsonNode node) {
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
