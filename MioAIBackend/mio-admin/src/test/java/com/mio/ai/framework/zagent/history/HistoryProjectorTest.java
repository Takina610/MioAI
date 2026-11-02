package com.mio.ai.framework.zagent.history;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

/**
 * 请求投影纯单元测试：重点验证「孤立工具结果被丢弃」——
 * 上游要求 tool 消息必须配对前一条 assistant 的 tool_calls，否则整个请求 400。
 */
class HistoryProjectorTest {

    @Test
    void orphanToolResultIsDropped() {
        List<ConversationEntry> entries = List.of(
                ConversationEntry.user("hi"),
                // 没有对应 assistant tool_call 的工具结果（压缩/截断边界可能产生）
                ConversationEntry.toolResult("call-1", "Read", "content", false));
        List<Message> messages = HistoryProjector.project(List.of(), entries);

        Assertions.assertEquals(1, messages.size(), "孤立工具结果应被丢弃: " + messages);
        Assertions.assertFalse(messages.stream().anyMatch(m -> m instanceof ToolResponseMessage));
    }

    @Test
    void pairedToolResultIsKept() {
        List<ConversationEntry> entries = List.of(
                ConversationEntry.user("hi"),
                ConversationEntry.assistant("", List.of(
                        new ConversationEntry.ToolCallInput("call-1", "Read", "{\"file_path\":\"a\"}"))),
                ConversationEntry.toolResult("call-1", "Read", "file content", false));
        List<Message> messages = HistoryProjector.project(List.of(), entries);

        Assertions.assertEquals(3, messages.size(), messages.toString());
        Assertions.assertTrue(messages.get(1) instanceof AssistantMessage);
        Assertions.assertTrue(messages.get(2) instanceof ToolResponseMessage);
    }

    @Test
    void toolResultWithoutPrecedingAssistantInMiddleStillDropped() {
        // 中间出现未声明的工具结果：只丢弃它，前后合法内容保持不变
        List<ConversationEntry> entries = List.of(
                ConversationEntry.user("q1"),
                ConversationEntry.assistant("a1", List.of()),
                ConversationEntry.toolResult("ghost", "Bash", "orphan", false),
                ConversationEntry.user("q2"));
        List<Message> messages = HistoryProjector.project(List.of(), entries);

        Assertions.assertEquals(3, messages.size(), messages.toString());
        Assertions.assertFalse(messages.stream().anyMatch(m -> m instanceof ToolResponseMessage));
    }

    private static com.mio.ai.framework.zagent.tools.ToolMedia media(String mimeType, byte[] bytes) {
        return media(mimeType, java.util.Base64.getEncoder().encodeToString(bytes));
    }

    private static com.mio.ai.framework.zagent.tools.ToolMedia media(String mimeType, String base64) {
        // 字段顺序：mimeType, base64, originalSize, transformedSize, resized, compressed, ...
        return new com.mio.ai.framework.zagent.tools.ToolMedia(
                mimeType, base64, 0, 0, false, false, 0, 0, 0, 0);
    }

    @Test
    void oversizedMediaIsOmittedInsteadOfBreakingTheRequest() {
        // 单张超过上限：整张省略，其余文本结果与请求结构保持完整
        byte[] huge = new byte[HistoryProjector.MAX_MEDIA_BYTES_PER_ITEM + 1];
        List<ConversationEntry> entries = List.of(
                ConversationEntry.user("look"),
                ConversationEntry.assistant("", List.of(
                        new ConversationEntry.ToolCallInput("c1", "Read", "{}"))),
                ConversationEntry.toolResult("c1", "Read", "[Attached image]", false,
                        List.of(media("image/png", huge))));

        List<Message> messages = HistoryProjector.project(List.of(), entries);

        // user + assistant(toolCall) + toolResponse，无媒体 user 消息
        Assertions.assertEquals(3, messages.size(), messages.toString());
        Assertions.assertFalse(messages.stream()
                .filter(m -> m instanceof org.springframework.ai.chat.messages.UserMessage)
                .anyMatch(m -> !((org.springframework.ai.chat.messages.UserMessage) m).getMedia().isEmpty()));
    }

    @Test
    void malformedBase64IsSkippedWithoutFailing() {
        List<ConversationEntry> entries = List.of(
                ConversationEntry.user("look"),
                ConversationEntry.assistant("", List.of(
                        new ConversationEntry.ToolCallInput("c1", "Read", "{}"))),
                ConversationEntry.toolResult("c1", "Read", "[Attached image]", false,
                        List.of(media("image/png", "!!!not-base64!!!"))));

        List<Message> messages = HistoryProjector.project(List.of(), entries);
        Assertions.assertEquals(3, messages.size(), messages.toString());
    }

    @Test
    void smallMediaIsProjectedAsUserMessage() {
        byte[] small = new byte[1024];
        List<ConversationEntry> entries = List.of(
                ConversationEntry.user("look"),
                ConversationEntry.assistant("", List.of(
                        new ConversationEntry.ToolCallInput("c1", "Read", "{}"))),
                ConversationEntry.toolResult("c1", "Read", "[Attached image]", false,
                        List.of(media("image/png", small))));

        List<Message> messages = HistoryProjector.project(List.of(), entries);
        Assertions.assertEquals(4, messages.size(), messages.toString());
    }
}
