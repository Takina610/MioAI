package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.ASK_USER_QUESTION;

/**
 * AskUserQuestion 工具（zcode handlers/ask-user-question.ts 的对位移植）：
 * 校验问题结构 → 推 question 事件 → 阻塞等待用户经 /bot/answer 提交的答案 →
 * 按答案组装模型回执；超时/未答走 zcode"未作答"分支，任务不中断。
 */
final class AskUserQuestionTool {

    /** 等待用户作答的上限（心跳在此期间持续保活连接） */
    private static final long WAIT_TIMEOUT_MS = 10 * 60_000;

    private static final AtomicLong ID_SEQ = new AtomicLong();

    private AskUserQuestionTool() {
    }

    static ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "questions":{"type":"array","minItems":1,"maxItems":4,"items":{"type":"object","properties":{
                "question":{"type":"string","description":"The complete question to ask the user. Should be clear, specific, and end with a question mark."},
                "header":{"type":"string","description":"Very short label displayed as a chip/tag (max 12 chars)."},
                "options":{"type":"array","minItems":2,"maxItems":4,"items":{"type":"object","properties":{
                "key":{"type":"string","description":"Short choice label YOU pick, e.g. A/B/C, 1/2/3, or a compact word. Must be unique within the question; the user may reference it in free-text answers (e.g. 'A, but cheaper')"},
                "label":{"type":"string","description":"The display text for this option that the user will see after the key. Should be concise (1-5 words) and clearly describe the choice."},
                "description":{"type":"string","description":"Explanation of what this option means or what will happen if chosen."},
                "preview":{"type":"string","description":"Optional content rendered as markdown in a monospace box when this option is focused, for comparing concrete artifacts."}},
                "required":["key","label","description"]}}}},
                "required":["question","header","options"]}}},
                "required":["questions"]}""";
return ToolEntry.ofMutable("AskUserQuestion", ASK_USER_QUESTION, schema, 0, AskUserQuestionTool::execute);
    }

    static String execute(JsonNode input, ToolContext ctx) {
        if (ctx.events == null) {
            throw new ToolUseFailure(40, "AskUserQuestion is not available in this context "
                    + "(no interactive user channel).");
        }
        List<Map<String, Object>> payload = parseAndValidate(input);
        String id = "q_" + ID_SEQ.incrementAndGet();
        if (QuestionGate.instance().register(ctx.chatId, id, payload) == null) {
            throw new ToolUseFailure(41, "Another question is already waiting for the user's answer "
                    + "in this conversation.");
        }

        ctx.events.question(id, payload);
        List<QuestionGate.Answer> answers = QuestionGate.instance().await(ctx.chatId, WAIT_TIMEOUT_MS);
        if (answers == null || answers.isEmpty()) {
            ctx.events.questionAnswered(id, List.of());
            return "The user did not provide answers to these questions. "
                    + "Continue using your best judgment; do not treat this as a rejection "
                    + "or invent a user preference.";
        }

        List<Map<String, Object>> answerPayload = new ArrayList<>();
        StringBuilder model = new StringBuilder("User has answered your questions: ");
        boolean first = true;
        for (QuestionGate.Answer answer : answers) {
            String question = questionText(payload, answer.index());
            boolean hasSelection = answer.selections() != null && !answer.selections().isEmpty();
            String custom = answer.custom() == null ? "" : answer.custom().trim();
            // 自定义回答可能引用选项标签（如 "A，但希望更便宜"）——选项与自由文本都原样透传，
            // 模型按「引用选项 + 补充说明」理解，不做任何改写
            String joined;
            if (hasSelection && !custom.isEmpty()) {
                joined = String.join(", ", answer.selections()) + " — plus free-text: " + custom;
            } else if (hasSelection) {
                joined = String.join(", ", answer.selections());
            } else {
                joined = custom;
            }
            if (!first) {
                model.append(' ');
            }
            first = false;
            model.append('"').append(question).append('"').append('=')
                    .append('"').append(joined).append('"').append('.');
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", answer.index());
            List<String> chosen = new ArrayList<>();
            if (hasSelection) {
                chosen.addAll(answer.selections());
            }
            if (!custom.isEmpty()) {
                chosen.add(custom);
            }
            item.put("selections", chosen);
            answerPayload.add(item);
        }
        ctx.events.questionAnswered(id, answerPayload);
        model.append(" If an answer references an option key with additions, treat it as that option "
                + "plus the stated modifications. You can now continue with the user's answers in mind.");
        return model.toString();
    }

    private static List<Map<String, Object>> parseAndValidate(JsonNode input) {
        if (input == null) {
            throw new ToolUseFailure(1, "questions must be an array of 1-4 questions.");
        }
        JsonNode questionsNode = input.get("questions");
        List<JsonNode> raw;
        if (questionsNode != null && questionsNode.isArray()) {
            raw = new ArrayList<>();
            questionsNode.forEach(raw::add);
        } else if (input.has("question") || input.has("options")) {
            // 宽容归一（zcode resolveInput 语义）：模型常把单个问题对象平铺在顶层
            // （question/header/options 直接作为根字段）——按单问题处理，不必失败重试
            raw = new ArrayList<>();
            raw.add(input);
        } else {
            throw new ToolUseFailure(1, "questions must be an array of 1-4 questions.");
        }
        if (raw.isEmpty() || raw.size() > 4) {
            throw new ToolUseFailure(2, "You may ask between 1 and 4 questions at once.");
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (int qi = 0; qi < raw.size(); qi++) {
            JsonNode q = raw.get(qi);
            String question = Args.str(q, "question");
            String header = Args.str(q, "header");
            if (question == null || question.isBlank()) {
                throw new ToolUseFailure(3, "questions[" + qi + "].question must not be empty.");
            }
            if (header == null || header.isBlank()) {
                header = "问题";
            }
            if (header.length() > 12) {
                header = header.substring(0, 12);
            }
            if (!q.has("options") || !q.get("options").isArray()) {
                throw new ToolUseFailure(4, "questions[" + qi + "].options must be an array of 2-4 options.");
            }
            List<JsonNode> rawOptions = new ArrayList<>();
            q.get("options").forEach(rawOptions::add);
            if (rawOptions.size() < 2 || rawOptions.size() > 4) {
                throw new ToolUseFailure(5, "questions[" + qi + "].options must contain between 2 and 4 options.");
            }
            List<Map<String, Object>> options = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (JsonNode rawOption : rawOptions) {
                String label = Args.str(rawOption, "label");
                String description = Args.str(rawOption, "description");
                String key = Args.str(rawOption, "key");
                if (key == null || key.isBlank()) {
                    // 宽容归一：模型漏 key 时按序补 A/B/C/D
                    key = String.valueOf((char) ('A' + keys.size()));
                }
                if (key.length() > 12) {
                    key = key.substring(0, 12);
                }
                if (label == null || label.isBlank()) {
                    throw new ToolUseFailure(6, "Every option needs a non-empty label.");
                }
                if ("Other".equalsIgnoreCase(label) || "其它".equals(label) || "其他".equals(label)) {
                    throw new ToolUseFailure(7, "Do not include an Other option; "
                            + "the client provides it automatically.");
                }
                if (keys.contains(key)) {
                    throw new ToolUseFailure(8, "Option keys must be unique: \"" + key + "\" repeats.");
                }
                keys.add(key);
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("key", key);
                option.put("label", label);
                option.put("description", description == null ? "" : description);
                String preview = Args.str(rawOption, "preview");
                if (preview != null && !preview.isBlank()) {
                    option.put("preview", preview);
                }
                options.add(option);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", question);
            item.put("header", header);
            item.put("options", options);
            payload.add(item);
        }
        return payload;
    }

    private static String questionText(List<Map<String, Object>> payload, int index) {
        if (index < 0 || index >= payload.size()) {
            return "(unknown question)";
        }
        return String.valueOf(payload.get(index).get("question"));
    }
}
