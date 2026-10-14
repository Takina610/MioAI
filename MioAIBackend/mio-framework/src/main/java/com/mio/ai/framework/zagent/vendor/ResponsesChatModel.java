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
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses (/responses) 协议适配器：
 * 系统词走 instructions、历史映射为 input 条目（message / function_call /
 * function_call_output），推理档映射 reasoning.effort，流式事件按 output_text /
 * reasoning / function_call_arguments 增量映射。
 */
public class ResponsesChatModel extends VendorSseChatModel {

    private static final Map<String, String> EFFORT_MAP = Map.of(
            "minimal", "low", "low", "low", "medium", "medium", "high", "high",
            "xhigh", "high", "max", "high");

    /** 单次流式调用的可变状态 */
    private static final class Session {
        Integer inputTokens;
        Integer outputTokens;
        boolean finishEmitted;
    }

    public ResponsesChatModel(String baseUrl, String apiKey, String defaultModel) {
        super(baseUrl, apiKey, defaultModel);
    }

    @Override
    protected Object newSession() {
        return new Session();
    }

    @Override
    protected Request buildRequest(Prompt prompt) {
        RequestSpec spec = specOf(prompt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", spec.model());
        body.put("stream", true);

        List<Object> input = new ArrayList<>();
        StringBuilder instructions = new StringBuilder();
        for (Message message : prompt.getInstructions()) {
            switch (message) {
                case SystemMessage sm -> {
                    if (!instructions.isEmpty()) {
                        instructions.append("\n\n");
                    }
                    instructions.append(sm.getText());
                }
                case UserMessage um -> input.add(Map.of(
                        "role", "user",
                        "content", List.of(Map.of("type", "input_text", "text", um.getText()))));
                case ToolResponseMessage trm -> {
                    for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("type", "function_call_output");
                        item.put("call_id", resp.id());
                        item.put("output", resp.responseData() == null ? "" : resp.responseData());
                        input.add(item);
                    }
                }
                case AssistantMessage am -> {
                    if (am.getText() != null && !am.getText().isEmpty()) {
                        input.add(Map.of(
                                "role", "assistant",
                                "content", List.of(Map.of("type", "output_text", "text", am.getText()))));
                    }
                    if (am.getToolCalls() != null) {
                        for (AssistantMessage.ToolCall call : am.getToolCalls()) {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("type", "function_call");
                            item.put("call_id", call.id());
                            item.put("name", call.name());
                            item.put("arguments", call.arguments() == null ? "{}" : call.arguments());
                            input.add(item);
                        }
                    }
                }
                default -> {
                    // 未知消息类型跳过
                }
            }
        }
        if (!instructions.isEmpty()) {
            body.put("instructions", instructions.toString());
        }
        body.put("input", input);

        if (spec.tools() != null && !spec.tools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolCallback tool : spec.tools()) {
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("type", "function");
                def.put("name", tool.getToolDefinition().name());
                def.put("description", tool.getToolDefinition().description());
                def.put("parameters", schemaOf(tool));
                def.put("strict", false);
                tools.add(def);
            }
            body.put("tools", tools);
        }

        String effort = EFFORT_MAP.get(spec.reasoningEffort() == null ? "" : spec.reasoningEffort());
        if (effort != null) {
            body.put("reasoning", Map.of("effort", effort));
        }

        RequestBody payload = RequestBody.create(
                JacksonUtil.writeValueAsString(body), MediaType.parse("application/json"));
        return new Request.Builder().url(baseUrl + "/responses")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(payload)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleEventData(Object sessionObj, Map<String, Object> event, FluxSink<ChatResponse> sink) {
        Session session = (Session) sessionObj;
        switch (str(event.get("type"))) {
            case "response.output_text.delta" -> sink.next(textChunk(str(event.get("delta"))));
            case "response.reasoning_text.delta", "response.reasoning_summary_text.delta" ->
                    sink.next(thinkingChunk(str(event.get("delta"))));
            case "response.output_item.added" -> {
                if (event.get("item") instanceof Map<?, ?> item
                        && "function_call".equals(item.get("type"))) {
                    String args = str(item.get("arguments"));
                    sink.next(toolChunk(new AssistantMessage.ToolCall(
                            str(item.get("call_id")), "function", str(item.get("name")),
                            args.isEmpty() ? "" : args)));
                }
            }
            case "response.function_call_arguments.delta" ->
                    sink.next(toolChunk(new AssistantMessage.ToolCall(
                            "", "function", "", str(event.get("delta")))));
            case "response.completed", "response.incomplete" -> {
                if (event.get("response") instanceof Map<?, ?> response) {
                    if (response.get("usage") instanceof Map<?, ?> usage) {
                        session.inputTokens = intOf(usage.get("input_tokens"), 0);
                        session.outputTokens = intOf(usage.get("output_tokens"), 0);
                    }
                    sink.next(finishChunk("response.incomplete".equals(str(event.get("type")))
                            ? "length" : "stop", session.inputTokens, session.outputTokens));
                    session.finishEmitted = true;
                }
            }
            case "response.failed" -> {
                Object error = event.get("response") instanceof Map<?, ?> response ? response.get("error") : null;
                sink.error(new IllegalStateException("Responses 流错误: " + (error == null ? event : error)));
            }
            case "error" -> sink.error(new IllegalStateException("Responses 流错误: "
                    + (event.get("message") == null ? event : event.get("message"))));
            default -> {
                // 其余事件（created/in_progress/输出项完成等）无需处理
            }
        }
    }

    @Override
    protected void onFinish(Object sessionObj, java.util.function.Consumer<ChatResponse> sink) {
        Session session = (Session) sessionObj;
        // 正常流以 response.completed 收尾（已发 finishChunk）；连接被掐断时兜底补一发
        if (!session.finishEmitted) {
            sink.accept(finishChunk("stop", session.inputTokens, session.outputTokens));
        }
    }
}
