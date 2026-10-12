package com.mio.ai.bot.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mio.ai.bot.agent.UiBlockValidator.cap;
import static com.mio.ai.bot.agent.UiBlockValidator.text;

/**
 * 流程/交互类与轻量信息类块校验：table / tasks / form / actions / status（第二期既有）
 * 与 timeline / quote / metric / choice（第三期新增）。
 */
final class UiExtendedBlockValidator {

    private static final int MAX_COLUMNS = 8;
    private static final int MAX_ROWS = 12;
    private static final int MAX_TASKS = 20;
    private static final int MAX_OPTIONS = 6;

    private static final Set<String> TASK_STATUSES = Set.of("todo", "doing", "done");
    private static final Set<String> STATUS_VALUES = Set.of("todo", "doing", "done", "failed");
    private static final Set<String> FIELD_TYPES = Set.of("text", "textarea", "number", "select");
    private static final Set<String> TRENDS = Set.of("up", "down", "flat");

    private UiExtendedBlockValidator() {
    }

    static Map<String, Object> validateTable(JsonNode raw) {
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

    static List<Map<String, Object>> validateTasks(JsonNode raw) {
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

    static Map<String, Object> validateForm(JsonNode raw) {
        JsonNode rawFields = raw.get("fields");
        if (rawFields == null || !rawFields.isArray() || rawFields.isEmpty()) {
            throw new IllegalArgumentException("form needs a non-empty fields array");
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (JsonNode item : rawFields) {
            if (fields.size() >= MAX_TASKS || !item.isObject()) {
                continue;
            }
            String label = text(item, "label");
            if (label == null) {
                continue;
            }
            String type = text(item, "type");
            if (type == null || !FIELD_TYPES.contains(type)) {
                type = "text";
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("label", label);
            field.put("type", type);
            // select 无选项时降级为普通文本输入（比整块丢弃更宽容）
            if ("select".equals(type)) {
                List<String> options = new ArrayList<>();
                if (item.get("options") != null && item.get("options").isArray()) {
                    for (JsonNode opt : item.get("options")) {
                        if (options.size() >= MAX_COLUMNS) {
                            break;
                        }
                        String option = cap(opt.isTextual() ? opt.asText() : String.valueOf(opt));
                        if (!option.isBlank()) {
                            options.add(option);
                        }
                    }
                }
                if (options.isEmpty()) {
                    field.put("type", "text");
                } else {
                    field.put("options", options);
                }
            }
            String placeholder = text(item, "placeholder");
            if (placeholder != null) {
                field.put("placeholder", placeholder);
            }
            if (item.path("required").asBoolean(false)) {
                field.put("required", true);
            }
            fields.add(field);
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("form needs 1-" + MAX_TASKS + " fields each with a non-empty label");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fields", fields);
        String submitLabel = text(raw, "submitLabel");
        result.put("submitLabel", submitLabel != null ? submitLabel : "提交");
        return result;
    }

    /**
     * 业务操作白名单：action.op 需要后端真实处理的操作 id。AI 无法自行开通——
     * 未注册的 op 一律按未授权丢弃（安全边界：模型只能生成 prompt 类聊天按钮，
     * 业务操作必须由后端显式注册后才可下发）。
     */
    private static final Set<String> OP_WHITELIST = Set.of();

    static List<Map<String, Object>> validateActions(JsonNode raw) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (actions.size() >= MAX_OPTIONS || !item.isObject()) {
                    continue;
                }
                String label = text(item, "label");
                String prompt = text(item, "prompt");
                if (label == null) {
                    continue;
                }
                if (item.hasNonNull("op") && !OP_WHITELIST.contains(item.get("op").asText())) {
                    // 带未授权 op 的按钮丢弃并告知模型：改用 prompt 型按钮
                    throw new IllegalArgumentException("action '" + label
                            + "' uses op '" + item.get("op").asText()
                            + "' which is not an authorized operation; use prompt instead");
                }
                if (prompt == null) {
                    continue;
                }
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("label", label);
                action.put("prompt", prompt);
                String style = text(item, "style");
                action.put("style", "primary".equals(style) ? "primary" : "default");
                actions.add(action);
            }
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("actions needs 1-" + MAX_OPTIONS
                    + " buttons each with label and prompt (an unregistered op was rejected if provided)");
        }
        return actions;
    }

    static List<Map<String, Object>> validateStatus(JsonNode raw) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (steps.size() >= MAX_TASKS || !item.isObject()) {
                    continue;
                }
                String stepText = text(item, "text");
                if (stepText == null) {
                    continue;
                }
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("text", stepText);
                String status = text(item, "status");
                step.put("status", status != null && STATUS_VALUES.contains(status) ? status : "todo");
                steps.add(step);
            }
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("status needs 1-" + MAX_TASKS + " steps each with a non-empty text");
        }
        return steps;
    }

    // ---------- 第三期新增：timeline / quote / metric / choice ----------

    static List<Map<String, Object>> validateTimeline(JsonNode raw) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (events.size() >= MAX_TASKS || !item.isObject()) {
                    continue;
                }
                String title = text(item, "title");
                if (title == null) {
                    continue;
                }
                Map<String, Object> event = new LinkedHashMap<>();
                String time = text(item, "time");
                if (time != null) {
                    event.put("time", time);
                }
                event.put("title", title);
                String desc = text(item, "desc");
                if (desc != null) {
                    event.put("desc", desc);
                }
                events.add(event);
            }
        }
        if (events.isEmpty()) {
            throw new IllegalArgumentException("timeline needs 1-" + MAX_TASKS
                    + " events each with a non-empty title");
        }
        return events;
    }

    static Map<String, Object> validateQuote(JsonNode raw) {
        String quoteText = text(raw, "text");
        if (quoteText == null) {
            throw new IllegalArgumentException("quote needs a non-empty text");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("text", quoteText);
        String author = text(raw, "author");
        if (author != null) {
            fields.put("author", author);
        }
        String source = text(raw, "source");
        if (source != null) {
            fields.put("source", source);
        }
        return fields;
    }

    static List<Map<String, Object>> validateMetric(JsonNode raw) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (items.size() >= 4 || !item.isObject()) {
                    continue;
                }
                String label = text(item, "label");
                String value = text(item, "value");
                if (label == null || value == null) {
                    continue;
                }
                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("label", label);
                metric.put("value", value);
                String unit = text(item, "unit");
                if (unit != null) {
                    metric.put("unit", unit);
                }
                String trend = text(item, "trend");
                if (trend != null) {
                    metric.put("trend", TRENDS.contains(trend) ? trend : "flat");
                }
                items.add(metric);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("metric needs 1-4 items each with label and value");
        }
        return items;
    }

    static List<Map<String, Object>> validateChoice(JsonNode raw) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (raw != null && raw.isArray()) {
            for (JsonNode item : raw) {
                if (options.size() >= MAX_OPTIONS || !item.isObject()) {
                    continue;
                }
                String label = text(item, "label");
                if (label == null) {
                    continue;
                }
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("label", label);
                String prompt = text(item, "prompt");
                if (prompt != null) {
                    option.put("prompt", prompt);
                }
                options.add(option);
            }
        }
        if (options.size() < 2) {
            throw new IllegalArgumentException("choice needs 2-" + MAX_OPTIONS
                    + " options each with a non-empty label");
        }
        return options;
    }
}
