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
                "label":{"type":"string","description":"The display text for this option that the user will see and select. Should be concise (1-5 words) and clearly describe the choice."},
                "description":{"type":"string","description":"Explanation of what this option means or what will happen if chosen."},
                "preview":{"type":"string","description":"Optional content rendered as markdown in a monospace box when this option is focused, for comparing concrete artifacts."}},
                "required":["label","description"]}},
                "multiSelect":{"type":"boolean","description":"Set to true to allow multiple answers for this question."}}},
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
            List<String> chosen = new ArrayList<>();
            if (answer.selections() != null) {
                chosen.addAll(answer.selections());
            }
            if (answer.custom() != null && !answer.custom().isBlank()) {
                chosen.add(answer.custom());
            }
            String joined = String.join(", ", chosen);
            if (!first) {
                model.append(' ');
            }
            first = false;
            model.append('"').append(question).append('"').append('=')
                    .append('"').append(joined).append('"').append('.');
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", answer.index());
            item.put("selections", chosen);
            answerPayload.add(item);
        }
        ctx.events.questionAnswered(id, answerPayload);
        model.append(" You can now continue with the user's answers in mind.");
        return model.toString();
    }

    private static List<Map<String, Object>> parseAndValidate(JsonNode input) {
        if (input == null || !input.has("questions") || !input.get("questions").isArray()) {
            throw new ToolUseFailure(1, "questions must be an array of 1-4 questions.");
        }
        List<JsonNode> raw = new ArrayList<>();
        input.get("questions").forEach(raw::add);
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
            List<String> labels = new ArrayList<>();
            for (JsonNode rawOption : rawOptions) {
                String label = Args.str(rawOption, "label");
                String description = Args.str(rawOption, "description");
                if (label == null || label.isBlank()) {
                    throw new ToolUseFailure(6, "Every option needs a non-empty label.");
                }
                if ("Other".equalsIgnoreCase(label) || "其它".equals(label) || "其他".equals(label)) {
                    throw new ToolUseFailure(7, "Do not include an Other option; "
                            + "the client provides it automatically.");
                }
                if (labels.contains(label)) {
                    throw new ToolUseFailure(8, "Option labels must be unique: \"" + label + "\" repeats.");
                }
                labels.add(label);
                Map<String, Object> option = new LinkedHashMap<>();
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
            item.put("multiSelect", Args.bool(q, "multiSelect"));
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
