package com.mio.ai.framework.zagent.history;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 群聊成员发言的水合回归测试。
 * <p>群主是调度层、成员是执行层：成员的回答落库为 assistant 行里的 member 块，
 * 水合时必须回到请求历史（带署名），否则下一轮群主完全不知道自己派过谁、
 * 成员给过什么结论，只能凭记忆重派或编造成员观点。
 */
class ConversationHydratorMemberTest {

    private static String assistantRow(String blocks) {
        return blocks;
    }

    @Test
    void memberBlockBecomesSignedReminder() {
        String blocks = """
                [{"type":"text","text":"我让翻译官处理一下。"},\
                {"type":"member","id":"agent_1","memberId":42,"memberName":"翻译官","status":"done",\
                "text":"The weather is nice today, perfect for a walk."},\
                {"type":"text","text":"以上就是译文。"}]""";
        ConversationHydrator.Hydrated hydrated = ConversationHydrator.hydrate(List.of(
                new ConversationHydrator.DisplayRow("user", "[{\"type\":\"text\",\"text\":\"翻译一下\"}]", null),
                new ConversationHydrator.DisplayRow("assistant", assistantRow(blocks), null)));

        String joined = hydrated.entries().stream()
                .map(entry -> entry.text == null ? "" : entry.text)
                .reduce("", (a, b) -> a + "\n" + b);
        Assertions.assertTrue(joined.contains("翻译官"), "成员名必须出现，群主才知道是谁说的: " + joined);
        Assertions.assertTrue(joined.contains("The weather is nice today"),
                "成员正文必须进入请求历史，否则下一轮群主丢失成员成果: " + joined);
        // 群主自己的正文不能被成员发言顶掉
        Assertions.assertTrue(joined.contains("我让翻译官处理一下。"), joined);
        Assertions.assertTrue(joined.contains("以上就是译文。"), joined);
    }

    @Test
    void memberHistoryIsArchivedPerMember() {
        String blocks = """
                [{"type":"member","id":"agent_1","memberId":42,"memberName":"翻译官","status":"done",\
                "text":"第一次译文"},\
                {"type":"member","id":"agent_2","memberId":7,"memberName":"审查员","status":"done",\
                "text":"审查结论"}]""";
        ConversationHydrator.Hydrated hydrated = ConversationHydrator.hydrate(List.of(
                new ConversationHydrator.DisplayRow("assistant", blocks, null)));

        Map<Long, List<ConversationHydrator.MemberUtterance>> history = hydrated.memberHistory();
        Assertions.assertEquals(2, history.size(), "两个成员各自归档: " + history);
        Assertions.assertEquals("翻译官", history.get(42L).get(0).memberName());
        Assertions.assertEquals("第一次译文", history.get(42L).get(0).text());
        Assertions.assertEquals("审查结论", history.get(7L).get(0).text());
    }

    @Test
    void failedOrEmptyMemberBlockIsNotInjected() {
        String blocks = """
                [{"type":"member","id":"agent_1","memberId":42,"memberName":"翻译官","status":"done",\
                "failed":true,"text":""}]""";
        ConversationHydrator.Hydrated hydrated = ConversationHydrator.hydrate(List.of(
                new ConversationHydrator.DisplayRow("assistant", blocks, null)));

        Assertions.assertTrue(hydrated.entries().isEmpty(),
                "成员没有产出内容时不应注入任何东西: " + hydrated.entries());
        Assertions.assertTrue(hydrated.memberHistory().isEmpty(), hydrated.memberHistory().toString());
    }

    @Test
    void memberBlocksPreserveOrderAgainstToolAndTextBlocks() {
        String blocks = """
                [{"type":"text","text":"先看代码。"},\
                {"type":"tool","id":"t1","tool":"Read","args":"{}","result":"file body","status":"done"},\
                {"type":"member","id":"agent_1","memberId":42,"memberName":"审查员","status":"done",\
                "text":"发现一个空指针。"},\
                {"type":"text","text":"结论如上。"}]""";
        ConversationHydrator.Hydrated hydrated = ConversationHydrator.hydrate(List.of(
                new ConversationHydrator.DisplayRow("assistant", blocks, null)));

        List<ConversationEntry> entries = hydrated.entries();
        int memberReminder = -1;
        for (int i = 0; i < entries.size(); i++) {
            ConversationEntry entry = entries.get(i);
            if (entry.kind == ConversationEntry.Kind.REMINDER && "member".equals(entry.source)) {
                memberReminder = i;
            }
        }
        Assertions.assertTrue(memberReminder > 0, "成员提醒应插在文本/工具之后: " + entries);
        // 成员发言之前的工具结果必须已配对收束（否则提供方 400）
        boolean sawToolResult = entries.stream()
                .anyMatch(entry -> entry.kind == ConversationEntry.Kind.TOOL);
        Assertions.assertTrue(sawToolResult, "工具块不能被成员块吞掉: " + entries);
    }
}
