package com.mio.ai.framework.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 单轮模型流式响应的增量收集器（ZCode 风格块流）。
 * <p>推理/正文增量实时外抛；工具调用同样实时外抛——首个分片（携带 id/name）触发
 * {@code onToolUse}，后续参数分片逐段触发 {@code onToolArgs}；同时聚合成完整
 * ChatResponse 供工具执行与记忆落库：文本按序拼接，工具调用按"id 出现即新调用、
 * 无 id 视为参数续片"合并（OpenAI 流式协议），末尾仅含 usage 的空响应只取用量。
 */
public class StreamTurnCollector {

    private final Consumer<String> thinkingDeltaSink;
    private final Consumer<String> answerDeltaSink;
    private final BiConsumer<String, String> toolUseSink;
    private final BiConsumer<String, String> toolArgsSink;

    private final StringBuilder text = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

    // 正在累积参数的工具调用（同一时刻只有一个未闭合：OpenAI 流式按序输出各调用的分片）
    private String pendingToolCallId;
    private String pendingToolCallType;
    private String pendingToolCallName;
    private final StringBuilder pendingToolCallArgs = new StringBuilder();

    private Usage usage;

    /** 本轮是否已向外发出任何内容（重试边界哨兵：发出后瞬态失败不再重试） */
    private boolean emitted;

    /** 本轮是否已向外发出思考/正文/工具调用任一内容（zcode 重试边界：false 才允许无感重试） */
    public boolean hasEmitted() {
        return emitted;
    }

    public StreamTurnCollector(Consumer<String> thinkingDeltaSink,
                               Consumer<String> answerDeltaSink,
                               BiConsumer<String, String> toolUseSink,
                               BiConsumer<String, String> toolArgsSink) {
        this.thinkingDeltaSink = thinkingDeltaSink;
        this.answerDeltaSink = answerDeltaSink;
        this.toolUseSink = toolUseSink;
        this.toolArgsSink = toolArgsSink;
    }

    public void accept(ChatResponse chunk) {
        if (chunk == null) {
            return;
        }
        if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
            this.usage = chunk.getMetadata().getUsage();
        }
        // 2.0 流式末尾会推一条仅含 usage 的空响应
        if (chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return;
        }
        AssistantMessage output = chunk.getResult().getOutput();

        Object reasoningDelta = output.getMetadata().get("reasoningContent");
        if (reasoningDelta instanceof String reasoningText && !reasoningText.isEmpty()) {
            // 兼容两种思考流格式：增量式（各 chunk 只含新增）与累积快照式
            // （如 opencode zen 网关，每个 chunk 都是从头到当前的全文）——后者做差分
            String delta = reasoningText;
            if (reasoning.length() > 0 && reasoningText.startsWith(reasoning.toString())) {
                delta = reasoningText.substring(reasoning.length());
            }
            if (!delta.isEmpty()) {
                reasoning.append(delta);
                thinkingDeltaSink.accept(delta);
                emitted = true;
            }
        }

        String textDelta = output.getText();
        if (textDelta != null && !textDelta.isEmpty()) {
            text.append(textDelta);
            answerDeltaSink.accept(textDelta);
            emitted = true;
        }

        mergeToolCalls(output.getToolCalls());
    }

    private void mergeToolCalls(List<AssistantMessage.ToolCall> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        for (AssistantMessage.ToolCall delta : deltas) {
            if (delta == null) {
                continue;
            }
            boolean startsNewCall = delta.id() != null && !delta.id().isBlank();
            if (startsNewCall) {
                flushPendingToolCall();
                pendingToolCallId = delta.id();
                pendingToolCallType = delta.type();
                pendingToolCallName = delta.name();
                // 新调用出现即通知前端（必须先于参数分片，保证前端/持久化先建好工具块）
                if (pendingToolCallName != null && !pendingToolCallName.isBlank()) {
                    toolUseSink.accept(pendingToolCallId, pendingToolCallName);
                    emitted = true;
                }
                appendArgs(delta);
            } else if (pendingToolCallName != null) {
                // 参数续片：个别实现会把 name 补在后续分片上
                if (pendingToolCallName.isBlank() && delta.name() != null && !delta.name().isBlank()) {
                    pendingToolCallName = delta.name();
                    toolUseSink.accept(pendingToolCallId, pendingToolCallName);
                }
                appendArgs(delta);
            } else {
                // 首个分片就没带 id（个别兼容端点的行为），照样开一个新调用
                pendingToolCallId = delta.id();
                pendingToolCallType = delta.type();
                pendingToolCallName = delta.name() != null ? delta.name() : "";
                if (!pendingToolCallName.isBlank()) {
                    toolUseSink.accept(pendingToolCallId, pendingToolCallName);
                }
                appendArgs(delta);
            }
        }
    }

    private void appendArgs(AssistantMessage.ToolCall delta) {
        if (delta.arguments() != null && !delta.arguments().isEmpty()) {
            pendingToolCallArgs.append(delta.arguments());
            toolArgsSink.accept(pendingToolCallId, delta.arguments());
        }
    }

    private void flushPendingToolCall() {
        if (pendingToolCallName != null) {
            toolCalls.add(new AssistantMessage.ToolCall(
                    pendingToolCallId, pendingToolCallType, pendingToolCallName, pendingToolCallArgs.toString()));
        }
        pendingToolCallId = null;
        pendingToolCallType = null;
        pendingToolCallName = null;
        pendingToolCallArgs.setLength(0);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    public String getText() {
        return text.toString();
    }

    public String getReasoning() {
        return reasoning.toString();
    }

    public Usage getUsage() {
        return usage;
    }

    /** 聚合出本轮完整响应（文本 + 完整工具调用 + 用量），供工具执行与记忆落库使用 */
    public ChatResponse build() {
        flushPendingToolCall();
        AssistantMessage assistant = AssistantMessage.builder()
                .content(text.toString())
                .toolCalls(toolCalls)
                .build();
        ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder();
        if (usage != null) {
            metadataBuilder.usage(usage);
        }
        return new ChatResponse(List.of(new Generation(assistant)), metadataBuilder.build());
    }
}
