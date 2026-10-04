package com.mio.ai.framework.zagent.compact;

import com.mio.ai.framework.zagent.history.ConversationEntry;
import com.mio.ai.framework.zagent.history.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩（zcode compact/ 的对位移植）：
 * 预算估算是字符/4；阈值 = 窗口 − 输出预留 − 13000 缓冲；
 * 自动/手动压缩用模型生成九段结构化摘要并整体替换请求历史（保留尾部近况）；
 * 微压缩就地老化久远的大体积工具结果。
 */
@Slf4j
public final class ConversationCompactor {

    private static final int BUFFER_TOKENS = 13_000;
    private static final int OUTPUT_RESERVE_TOKENS = 21_000;
    private static final int KEEP_TAIL_ENTRIES = 4;
    private static final int MAX_TOOL_RESULT_IN_SUMMARY = 2_000;
    private static final int MICROCOMPACT_TRIGGER_PERCENT = 60;
    private static final int MICROCOMPACT_KEEP_RECENT_ENTRIES = 10;
    private static final int MICROCOMPACT_KEEP_CHARS = 400;

    private final ChatModel chatModel;
    private final long contextWindowTokens;

    public ConversationCompactor(ChatModel chatModel, long contextWindowTokens) {
        this.chatModel = chatModel;
        this.contextWindowTokens = contextWindowTokens > 0 ? contextWindowTokens : 200_000;
    }

    /** zcode shouldAutoCompact：超阈值且已有 ≥2 轮助手内容 */
    public boolean shouldAutoCompact(ConversationState state) {
        long threshold = contextWindowTokens - Math.min(32_000, OUTPUT_RESERVE_TOKENS) - BUFFER_TOKENS;
        return state.estimateTokens() > threshold && countAssistantRounds(state) >= 2;
    }

    /** 微压缩触发线（未到自动压缩但已占用过半） */
    public boolean shouldMicrocompact(ConversationState state) {
        long softLine = (contextWindowTokens - BUFFER_TOKENS) * MICROCOMPACT_TRIGGER_PERCENT / 100;
        return state.estimateTokens() > softLine;
    }

    /**
     * 压缩：摘要旧条目 + 保留尾部近况，整体替换请求历史。
     *
     * @return 压缩摘要文本（供持久化边界与前端提示），失败返回 null
     */
    public String compact(ConversationState state) {
        List<ConversationEntry> entries = state.entries();
        if (entries.size() <= KEEP_TAIL_ENTRIES + 2) {
            return null;
        }
        List<ConversationEntry> toSummarize =
                new ArrayList<>(entries.subList(0, entries.size() - KEEP_TAIL_ENTRIES));
        List<ConversationEntry> tail =
                new ArrayList<>(entries.subList(entries.size() - KEEP_TAIL_ENTRIES, entries.size()));
        String summary = requestSummary(toSummarize);
        if (summary == null || summary.isBlank()) {
            return null;
        }
        List<ConversationEntry> replacement = new ArrayList<>();
        replacement.add(ConversationEntry.reminder("compact", summary));
        replacement.addAll(tail);
        state.replaceAll(replacement);
        return summary;
    }

    /** 微压缩：久远的大体积工具结果截头保留（模型步间调用，便宜且无模型开销） */
    public boolean microcompactIfNeeded(ConversationState state) {
        if (!shouldMicrocompact(state)) {
            return false;
        }
        List<ConversationEntry> entries = state.entries();
        boolean changed = false;
        int from = Math.max(0, entries.size() - MICROCOMPACT_KEEP_RECENT_ENTRIES);
        for (int i = 0; i < from; i++) {
            ConversationEntry entry = entries.get(i);
            if (entry.kind == ConversationEntry.Kind.TOOL
                    && entry.content != null && entry.content.length() > MICROCOMPACT_KEEP_CHARS * 4) {
                entries.set(i, ConversationEntry.toolResult(entry.toolCallId, entry.toolName,
                        entry.content.substring(0, MICROCOMPACT_KEEP_CHARS)
                                + "\n[...older tool output evicted by microcompaction...]",
                        entry.error));
                changed = true;
            }
        }
        return changed;
    }

    private String requestSummary(List<ConversationEntry> entries) {
        String transcript = renderTranscript(entries);
        if (transcript.isBlank()) {
            return null;
        }
        String system = """
                Your task is to create a detailed summary of the conversation so far, paying close attention \
                to the user's explicit requests and your steps taken in fulfilling them. The summary will be \
                given to another instance of yourself that will continue this conversation without access to \
                the original history. The summary must be self-contained so the work can continue seamlessly.
                Structure the summary with these sections:
                1. Primary Request and Intent
                2. Key Technical Concepts
                3. Files and Code Sections
                4. Errors and fixes (tracebacks, causes, solutions)
                5. Tool Usage (which tools were used and why)
                6. Pending / In-progress Work
                7. Current Work (what you were doing at the truncation point)
                8. Optional Next Step
                9. User Preferences and Communication Style
                Be precise with file paths, commands, and decisions. Write the summary in the conversation's language.""";
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(system),
                new UserMessage("Conversation to summarize:\n\n" + transcript)));
        try {
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("压缩摘要生成失败: {}", e.getMessage());
            return null;
        }
    }

    private String renderTranscript(List<ConversationEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (ConversationEntry entry : entries) {
            switch (entry.kind) {
                case USER -> sb.append("[USER] ").append(truncate(entry.text, 8_000)).append('\n');
                case ASSISTANT -> {
                    sb.append("[ASSISTANT] ").append(truncate(entry.text, 4_000)).append('\n');
                    for (ConversationEntry.ToolCallInput call : entry.toolCalls) {
                        sb.append("  [TOOL CALL] ").append(call.name()).append(' ')
                                .append(truncate(call.arguments(), 1_000)).append('\n');
                    }
                }
                case TOOL -> sb.append("[TOOL RESULT] ").append(truncate(entry.content,
                        MAX_TOOL_RESULT_IN_SUMMARY)).append('\n');
                case REMINDER -> sb.append("[CONTEXT] ").append(truncate(entry.text, 2_000)).append('\n');
            }
        }
        return sb.toString();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + " [...truncated]";
    }

    private long countAssistantRounds(ConversationState state) {
        return state.entries().stream()
                .filter(entry -> entry.kind == ConversationEntry.Kind.ASSISTANT)
                .count();
    }
}
