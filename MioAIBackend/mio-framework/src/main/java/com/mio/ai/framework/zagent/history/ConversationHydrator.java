package com.mio.ai.framework.zagent.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.framework.zagent.history.ConversationEntry.ToolCallInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话冷启动水合（zcode session-history-hydrator 的对位移植）：
 * 把 agent_message 展示行（blocks JSON）重建为完整的请求历史。
 * <p>一个助手行包含整轮多步内容，按"文本 → 工具组 → 文本 → …"切分成
 * 交替的 assistant/tool 条目；thinking 块不回放（提供方不接受历史推理）。
 * 压缩边界块（type=compact）之前的全部历史作废，摘要作为续接消息开头。
 * <p>群聊的成员发言块（type=member）同样要回到请求历史：成员是执行层（Worker），
 * 群主是调度层（Router），成员成果不回流会让群主在下一轮完全不知道自己派过谁、
 * 成员说过什么。发言以带署名的 system-reminder 注入，并按成员归档供委派时复用。
 */
public final class ConversationHydrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 展示行视图：role + blocks 原文 + plan 原文 */
    public record DisplayRow(String role, String blocksJson, String planJson) {
    }

    /** 一次成员发言（群聊水合产物）：成员身份 + 正文 */
    public record MemberUtterance(long memberId, String memberName, String text) {
    }

    /**
     * 水合结果：请求历史条目 + 从最后一行 plan 重建的任务清单 + 成员历史（成员ID → 历次发言）。
     * <p>成员历史只在群聊里有内容，供委派时把成员此前的回答内联进它的任务提示。
     */
    public record Hydrated(List<ConversationEntry> entries, List<TodoItem> todos,
                           Map<Long, List<MemberUtterance>> memberHistory) {

        public Hydrated(List<ConversationEntry> entries, List<TodoItem> todos) {
            this(entries, todos, Map.of());
        }
    }

    public static Hydrated hydrate(List<DisplayRow> rows) {
        List<ConversationEntry> entries = new ArrayList<>();
        List<TodoItem> todos = new ArrayList<>();
        Map<Long, List<MemberUtterance>> memberHistory = new LinkedHashMap<>();
        if (rows == null) {
            return new Hydrated(entries, todos, memberHistory);
        }
        int idSeq = 0;
        for (DisplayRow row : rows) {
            List<JsonNode> blocks = parseBlocks(row.blocksJson());
            if (containsCompactBoundary(blocks)) {
                entries.clear();
                entries.add(ConversationEntry.reminder("compact", compactSummary(blocks)));
                continue;
            }
            if ("user".equals(row.role())) {
                String text = joinText(blocks);
                // 空行（如压缩指令行只带标记块）与斜杠指令行（/compact ...）不是对话内容，不进请求历史
                String stripped = text.strip();
                if (!stripped.isEmpty() && !stripped.startsWith("/compact")) {
                    entries.add(ConversationEntry.user(text));
                }
            } else if ("assistant".equals(row.role())) {
                hydrateAssistantRow(blocks, entries, idSeq, memberHistory);
                idSeq += 100;
            }
            parseTodos(row.planJson()).ifPresent(parsed -> {
                todos.clear();
                todos.addAll(parsed);
            });
        }
        return new Hydrated(entries, todos, memberHistory);
    }

    /** 一个助手行 → 若干 assistant/tool/成员发言条目（文本、工具组与成员发言的原始时间序） */
    private static void hydrateAssistantRow(List<JsonNode> blocks, List<ConversationEntry> entries, int idBase,
                                            Map<Long, List<MemberUtterance>> memberHistory) {
        StringBuilder textBeforeTools = new StringBuilder();
        List<JsonNode> toolGroup = new ArrayList<>();
        for (JsonNode block : blocks) {
            String type = textOf(block, "type");
            if ("tool".equals(type)) {
                toolGroup.add(block);
            } else if ("text".equals(type)) {
                flushAssistantStep(textBeforeTools, toolGroup, entries, idBase);
                String text = textOf(block, "text");
                if (text != null && !text.isBlank()) {
                    if (textBeforeTools.length() > 0) {
                        textBeforeTools.append("\n\n");
                    }
                    textBeforeTools.append(text);
                }
            } else if ("member".equals(type)) {
                // 成员发言与群主文本/工具按时间序交错：先收束前面的群主步骤，再按署名注入
                flushAssistantStep(textBeforeTools, toolGroup, entries, idBase);
                hydrateMemberBlock(block, entries, memberHistory);
            }
            // thinking 等其他类型不回放
        }
        flushAssistantStep(textBeforeTools, toolGroup, entries, idBase);
    }

    /**
     * 成员发言块 → 带署名的 system-reminder + 成员历史归档。
     * <p>失败/空发言不注入（成员没产出内容，注入只会让群主误判它给过结论）；
     * 历史归档保留原文，委派时按需截断，避免长会话把成员上下文撑爆。
     */
    private static void hydrateMemberBlock(JsonNode block, List<ConversationEntry> entries,
                                           Map<Long, List<MemberUtterance>> memberHistory) {
        String text = textOf(block, "text");
        if (text == null || text.isBlank()) {
            return;
        }
        String name = textOf(block, "memberName");
        if (name == null || name.isBlank()) {
            name = "成员";
        }
        long memberId = block.hasNonNull("memberId") ? block.get("memberId").asLong() : -1L;
        entries.add(ConversationEntry.reminder("member",
                "群成员「" + name + "」此前的发言（成员产出，可作为后续委派与综合的依据）：\n" + text));
        if (memberId > 0) {
            memberHistory.computeIfAbsent(memberId, key -> new ArrayList<>())
                    .add(new MemberUtterance(memberId, name, text));
        }
    }

    private static void flushAssistantStep(StringBuilder text, List<JsonNode> toolGroup,
                                           List<ConversationEntry> entries, int idBase) {
        if (!toolGroup.isEmpty()) {
            List<ToolCallInput> calls = new ArrayList<>();
            for (int i = 0; i < toolGroup.size(); i++) {
                JsonNode block = toolGroup.get(i);
                String id = textOf(block, "id");
                if (id == null || id.isBlank()) {
                    id = "h" + (idBase + i);
                }
                calls.add(new ToolCallInput(id, textOf(block, "tool"), textOf(block, "args")));
            }
            entries.add(ConversationEntry.assistant(text.toString(), calls));
            for (int i = 0; i < toolGroup.size(); i++) {
                JsonNode block = toolGroup.get(i);
                String result = textOf(block, "result");
                if (result == null || result.isBlank()) {
                    // 中断轮次的未完成调用：合成错误结果保证配对完整（提供方要求逐调用回结果）
                    result = "<tool_use_error>Interrupted before completion</tool_use_error>";
                }
                entries.add(ConversationEntry.toolResult(
                        calls.get(i).id(), calls.get(i).name(), result, false));
            }
            toolGroup.clear();
            text.setLength(0);
        } else if (!text.isEmpty() && !text.toString().isBlank()) {
            // 纯文本助手步（无工具调用）：同样进入请求历史，否则上一轮回答在水合时丢失，
            // 模型会把历史里相邻的两个用户问题当成并列任务重复作答
            entries.add(ConversationEntry.assistant(text.toString(), List.of()));
            text.setLength(0);
        }
    }

    private static boolean containsCompactBoundary(List<JsonNode> blocks) {
        return blocks.stream().anyMatch(block -> "compact".equals(textOf(block, "type")));
    }

    private static String compactSummary(List<JsonNode> blocks) {
        return blocks.stream()
                .filter(block -> "compact".equals(textOf(block, "type")))
                .map(block -> textOf(block, "text"))
                .findFirst().orElse("");
    }

    private static String joinText(List<JsonNode> blocks) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : blocks) {
            if ("text".equals(textOf(block, "type"))) {
                String text = textOf(block, "text");
                if (text != null && !text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    private static java.util.Optional<List<TodoItem>> parseTodos(String planJson) {
        if (planJson == null || planJson.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            JsonNode plan = MAPPER.readTree(planJson);
            if (!plan.isArray() || plan.isEmpty()) {
                return java.util.Optional.empty();
            }
            List<TodoItem> parsed = new ArrayList<>();
            for (JsonNode step : plan) {
                parsed.add(TodoItem.fromDisplay(textOf(step, "description"), textOf(step, "status")));
            }
            return java.util.Optional.of(parsed);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static List<JsonNode> parseBlocks(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(blocksJson);
            List<JsonNode> blocks = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(blocks::add);
            }
            return blocks;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String textOf(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }
}
