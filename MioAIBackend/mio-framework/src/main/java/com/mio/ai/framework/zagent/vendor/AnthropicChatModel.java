package com.mio.ai.framework.zagent.vendor;

import com.mio.ai.common.utils.JacksonUtil;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages (v1/messages) 协议适配器：
 * 系统词走顶层 system、tool_result 挂 user 轮、思考走 thinking budget
 * （reasoning_effort → budget_tokens），流式事件按 content_block 增量映射。
 */
public class AnthropicChatModel extends VendorSseChatModel {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** 推理档 → thinking budget_tokens（none/未传关闭思考） */
    private static final Map<String, Integer> EFFORT_BUDGET = Map.of(
            "minimal", 1024, "low", 2048, "medium", 8192,
            "high", 16384, "xhigh", 32768, "max", 32768);

    /** 单次流式调用的可变状态 */
    private static final class Session {
        Integer inputTokens;
        Integer outputTokens;
        boolean finishEmitted;
    }

    public AnthropicChatModel(String baseUrl, String apiKey, String defaultModel) {
        super(baseUrl, apiKey, defaultModel);
    }

    @Override
    protected Object newSession() {
        return new Session();
    }

    /** BaseURL 以 /v1 结尾时补 /messages，否则补 /v1/messages */
    private String endpoint() {
        return baseUrl.endsWith("/v1") ? baseUrl + "/messages" : baseUrl + "/v1/messages";
    }

    @Override
    protected Request buildRequest(Prompt prompt) {
        RequestSpec spec = specOf(prompt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", spec.model());
        body.put("stream", true);
        body.put("max_tokens", maxTokens(spec.reasoningEffort(), prompt));

        List<Map<String, Object>> turns = new ArrayList<>();
        StringBuilder system = new StringBuilder();
        for (Message message : prompt.getInstructions()) {
            switch (message) {
                case SystemMessage sm -> {
                    if (!system.isEmpty()) {
                        system.append("\n\n");
                    }
                    system.append(sm.getText());
                }
                case UserMessage um -> turns.add(Map.of("role", "user",
                        "content", List.of(Map.of("type", "text", "text", um.getText()))));
                case ToolResponseMessage trm -> {
                    List<Map<String, Object>> results = new ArrayList<>();
                    for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("type", "tool_result");
                        item.put("tool_use_id", resp.id());
                        item.put("content", resp.responseData() == null ? "" : resp.responseData());
                        results.add(item);
                    }
                    turns.add(Map.of("role", "user", "content", results));
                }
                case AssistantMessage am -> {
                    List<Map<String, Object>> blocks = new ArrayList<>();
                    if (am.getText() != null && !am.getText().isEmpty()) {
                        blocks.add(Map.of("type", "text", "text", am.getText()));
                    }
                    if (am.getToolCalls() != null) {
                        for (AssistantMessage.ToolCall call : am.getToolCalls()) {
                            blocks.add(Map.of("type", "tool_use",
                                    "id", call.id(), "name", call.name(),
                                    "input", parseArgs(call.arguments())));
                        }
                    }
                    turns.add(Map.of("role", "assistant", "content", blocks));
                }
                default -> {
                    // 未知消息类型跳过
                }
            }
        }
        if (!system.isEmpty()) {
            body.put("system", system.toString());
        }
        body.put("messages", turns);

        if (spec.tools() != null && !spec.tools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolCallback tool : spec.tools()) {
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("name", tool.getToolDefinition().name());
                def.put("description", tool.getToolDefinition().description());
                def.put("input_schema", schemaOf(tool));
                tools.add(def);
            }
            body.put("tools", tools);
        }

        Integer budget = EFFORT_BUDGET.get(spec.reasoningEffort() == null ? "" : spec.reasoningEffort());
        if (budget != null) {
            // 思考开启时协议要求温度为 1（省略即可），且 budget 必须小于 max_tokens
            body.put("thinking", Map.of("type", "enabled", "budget_tokens", budget));
        }

        RequestBody payload = RequestBody.create(
                JacksonUtil.writeValueAsString(body), MediaType.parse("application/json"));
        return new Request.Builder().url(endpoint())
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .post(payload)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String args) {
        if (args == null || args.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JacksonUtil.readValue(args, Map.class);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** max_tokens：思考开启时取 budget+4096 保证大于 budget；否则选项值/默认 8192（Anthropic 必填） */
    private int maxTokens(String effort, Prompt prompt) {
        Integer budget = EFFORT_BUDGET.get(effort == null ? "" : effort);
        int base = 8192;
        if (prompt.getOptions() instanceof OpenAiChatOptions opts
                && opts.getMaxTokens() != null && opts.getMaxTokens() > base) {
            base = opts.getMaxTokens();
        }
        return budget == null ? base : Math.max(base, budget + 4096);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleEventData(Object sessionObj, Map<String, Object> event, FluxSink<ChatResponse> sink) {
        Session session = (Session) sessionObj;
        switch (str(event.get("type"))) {
            case "message_start" -> {
                if (event.get("message") instanceof Map<?, ?> message
                        && message.get("usage") instanceof Map<?, ?> usage) {
                    session.inputTokens = intOf(usage.get("input_tokens"), 0);
                }
            }
            case "content_block_start" -> {
                if (event.get("content_block") instanceof Map<?, ?> block
                        && "tool_use".equals(block.get("type"))) {
                    sink.next(toolChunk(new AssistantMessage.ToolCall(
                            str(block.get("id")), "function", str(block.get("name")), "")));
                }
            }
            case "content_block_delta" -> {
                if (!(event.get("delta") instanceof Map<?, ?> delta)) {
                    return;
                }
                switch (str(delta.get("type"))) {
                    case "text_delta" -> sink.next(textChunk(str(delta.get("text"))));
                    case "thinking_delta" -> sink.next(thinkingChunk(str(delta.get("thinking"))));
                    case "input_json_delta" -> sink.next(toolChunk(new AssistantMessage.ToolCall(
                            "", "function", "", str(delta.get("partial_json")))));
                    default -> {
                    }
                }
            }
            case "message_delta" -> {
                if (event.get("usage") instanceof Map<?, ?> usage) {
                    session.outputTokens = intOf(usage.get("output_tokens"), 0);
                }
                if (event.get("delta") instanceof Map<?, ?> delta && delta.get("stop_reason") != null) {
                    sink.next(finishChunk(mapStopReason(str(delta.get("stop_reason"))),
                            session.inputTokens, session.outputTokens));
                    session.finishEmitted = true;
                }
            }
            case "error" -> sink.error(new IllegalStateException("Anthropic 流错误: "
                    + (event.get("error") instanceof Map<?, ?> error ? error.get("message") : event)));
            default -> {
                // message_stop / ping / content_block_stop：无需处理
            }
        }
    }

    @Override
    protected void onFinish(Object sessionObj, java.util.function.Consumer<ChatResponse> sink) {
        Session session = (Session) sessionObj;
        // 正常流以 message_delta 收尾（已发 finishChunk）；连接被掐断时兜底补一发
        if (!session.finishEmitted) {
            sink.accept(finishChunk("stop", session.inputTokens, session.outputTokens));
        }
    }

    private static String mapStopReason(String reason) {
        return switch (reason) {
            case "tool_use" -> "tool_calls";
            case "max_tokens" -> "length";
            default -> "stop";
        };
    }
}
