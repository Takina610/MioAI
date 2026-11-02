package com.mio.ai.framework.zagent.compact;

import com.mio.ai.framework.zagent.history.ConversationEntry;
import com.mio.ai.framework.zagent.history.ConversationState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * 压缩切点纯单元测试：尾部保留段不得以工具结果开头，
 * 否则其对应的 assistant tool_call 会被摘要吞掉，投影后成为孤立 tool 消息（上游 400）。
 */
class ConversationCompactorTest {

    /** 只回固定摘要的假模型：压缩本身不发起真实网络请求 */
    private static ChatModel summaryModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder().content("SUMMARY").build())));
            }

            @Override
            public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
                return reactor.core.publisher.Flux.just(call(prompt));
            }
        };
    }

    private static ConversationEntry.ToolCallInput call(String id) {
        return new ConversationEntry.ToolCallInput(id, "Bash", "{}");
    }

    @Test
    void keepsToolResultTogetherWithItsAssistantCall() {
        ConversationState state = new ConversationState();
        // 填充历史，使尾部 4 条恰好以工具结果开头
        for (int i = 0; i < 6; i++) {
            state.add(ConversationEntry.user("u" + i));
            state.add(ConversationEntry.assistant("a" + i, List.of()));
        }
        state.add(ConversationEntry.assistant("", List.of(call("c1"))));
        state.add(ConversationEntry.toolResult("c1", "Bash", "out1", false));
        state.add(ConversationEntry.toolResult("c2", "Bash", "out2", false));
        state.add(ConversationEntry.assistant("done", List.of()));

        ConversationCompactor compactor = new ConversationCompactor(summaryModel(), 200_000);
        String summary = compactor.compact(state);

        Assertions.assertEquals("SUMMARY", summary);
        List<ConversationEntry> entries = state.entries();
        Assertions.assertTrue(entries.get(0).kind == ConversationEntry.Kind.REMINDER);
        // 保留段首条不能是孤立 TOOL（其 assistant 调用必须同时在保留段里）
        boolean hasOrphanAtHead = entries.size() > 1
                && entries.get(1).kind == ConversationEntry.Kind.TOOL;
        Assertions.assertFalse(hasOrphanAtHead, "保留段不应以工具结果开头: " + describe(entries));
    }

    @Test
    void skipsCompactWhenNothingMeaningfulToSummarize() {
        ConversationState state = new ConversationState();
        state.add(ConversationEntry.user("only"));
        ConversationCompactor compactor = new ConversationCompactor(summaryModel(), 200_000);

        Assertions.assertNull(compactor.compact(state));
        Assertions.assertEquals(1, state.entries().size());
    }

    /**
     * 尾部是一长串工具结果（并发工具调用后中断）时，压缩仍须生效——
     * 切点向后跳过会一路走到末尾、压缩永不触发，长会话必然撞上下文上限。
     */
    @Test
    void compactsEvenWhenTailIsAllToolResults() {
        ConversationState state = new ConversationState();
        for (int i = 0; i < 5; i++) {
            state.add(ConversationEntry.user("u" + i));
            state.add(ConversationEntry.assistant("a" + i, List.of()));
        }
        // 最后一轮：一个 assistant 发起了 6 个工具调用，结果全部在尾部
        state.add(ConversationEntry.user("run tools"));
        state.add(ConversationEntry.assistant("", List.of(call("t1"))));
        for (int i = 1; i <= 6; i++) {
            state.add(ConversationEntry.toolResult("t" + i, "Bash", "out" + i, false));
        }

        ConversationCompactor compactor = new ConversationCompactor(summaryModel(), 200_000);
        String summary = compactor.compact(state);

        Assertions.assertEquals("SUMMARY", summary, "尾部全是工具结果时压缩必须仍然生效");
        List<ConversationEntry> entries = state.entries();
        Assertions.assertEquals(ConversationEntry.Kind.REMINDER, entries.get(0).kind);
        // 保留段首条不能是孤立 TOOL
        Assertions.assertNotEquals(ConversationEntry.Kind.TOOL, entries.get(1).kind,
                "保留段不应以工具结果开头: " + describe(entries));
    }

    private static String describe(List<ConversationEntry> entries) {
        List<String> kinds = new ArrayList<>();
        for (ConversationEntry entry : entries) {
            kinds.add(entry.kind.name());
        }
        return kinds.toString();
    }
}
