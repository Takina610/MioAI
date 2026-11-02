package com.mio.ai.framework.zagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行超时分支回归：委派与普通工具的超时文案必须分流。
 * <p>委派超时的读者是模型——文案要说明"任务未完成、可换人/自行完成"；
 * 普通工具超时的读者也是模型但语义不同（重试单次调用即可）。两者混用会让模型
 * 在委派超时后误以为可以原样重发，或在读文件超时后以为可以换人。
 */
class ToolRegistryTimeoutSemanticsTest {

    private static ToolContext context() {
        return new ToolContext("chat-timeout", null, null, null, null, null, null, null);
    }

    private static final CountDownLatch NEVER = new CountDownLatch(1);

    @Test
    void delegationTimeoutExplainsBudgetAndNextSteps() {
        ToolRegistry registry = new ToolRegistry(context());
        registry.register(ToolEntry.ofDelegation("Agent", "delegate", "{}", 200,
                (input, ctx) -> {
                    NEVER.await(10, TimeUnit.SECONDS);
                    return "never";
                }));
        try {
            ToolRegistry.Executed result = registry.execute(
                    new ToolRegistry.PendingCall("t1", "Agent", "{}"));

            Assertions.assertTrue(result.error());
            String content = result.content();
            Assertions.assertTrue(content.contains("time budget"),
                    "委派超时应提到预算: " + content);
            Assertions.assertTrue(content.contains("was aborted"),
                    "委派超时应明确任务被中止: " + content);
            Assertions.assertTrue(content.contains("retry with a narrower prompt")
                            || content.contains("different agent"),
                    "委派超时应给出可操作的下一步: " + content);
            Assertions.assertFalse(content.contains("exceeded timeout of"),
                    "委派不应落到普通工具的超时文案: " + content);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void ordinaryToolTimeoutKeepsGenericMessage() {
        ToolRegistry registry = new ToolRegistry(context());
        registry.register(ToolEntry.ofMutable("Bash", "run", "{}", 200,
                (input, ctx) -> {
                    NEVER.await(10, TimeUnit.SECONDS);
                    return "never";
                }));
        try {
            ToolRegistry.Executed result = registry.execute(
                    new ToolRegistry.PendingCall("t2", "Bash", "{}"));

            Assertions.assertTrue(result.error());
            String content = result.content();
            Assertions.assertTrue(content.contains("exceeded timeout of"),
                    "普通工具超时保留通用文案: " + content);
            Assertions.assertFalse(content.contains("Delegation exceeded"),
                    "普通工具不应伪装成委派超时: " + content);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void timedOutDelegationStillReleasesPermitForNextCall() {
        // 委派超时后配额必须归还：否则一次超时就会永久吃掉一格的并行额度
        ToolRegistry registry = new ToolRegistry(context());
        registry.register(new AgentTool((type, description, prompt, background) -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new com.mio.ai.framework.zagent.subagent.SubagentLauncher.Result(
                    "agent_x", type, prompt, "late", 0, 0, 0, null);
        }, 300, 1).entry());
        try {
            ToolRegistry.Executed first = registry.execute(
                    new ToolRegistry.PendingCall("t3", "Agent",
                            "{\"description\":\"d\",\"prompt\":\"p\",\"subagent_type\":\"general-purpose\"}"));
            Assertions.assertTrue(first.error(), "第一次委派应超时");

            // 若配额未归还，第二次会立刻收到"too many agents"而不是再次超时
            ToolRegistry.Executed second = registry.execute(
                    new ToolRegistry.PendingCall("t4", "Agent",
                            "{\"description\":\"d\",\"prompt\":\"p\",\"subagent_type\":\"general-purpose\"}"));
            Assertions.assertFalse(second.content().contains("already running in this turn"),
                    "超时后配额应已归还: " + second.content());
        } finally {
            registry.shutdown();
        }
    }
}
