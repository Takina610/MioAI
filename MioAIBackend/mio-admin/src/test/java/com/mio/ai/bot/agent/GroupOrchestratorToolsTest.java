package com.mio.ai.bot.agent;

import com.mio.ai.framework.sandbox.SandboxSession;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.tools.contract.ToolEntry;
import com.mio.ai.framework.zagent.tools.contract.ToolRegistry;
import com.mio.ai.framework.zagent.tools.contract.ToolsetFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群聊调度层（Router）工具边界回归测试。
 * <p>文章的双层架构要点：Router 不亲自执行具体工作，否则会被新消息不断打断、
 * 且与 Worker 的上下文互相污染。本项目把这条边界做成工具面约束——调度层只装配
 * 分派与对外交互工具，环境工具（文件读写/终端/文件搜索）整体不注册。
 * <p>这里验证白名单确实生效：既要摘掉环境工具，也要保留分派与对外交互能力。
 */
class GroupOrchestratorToolsTest {

    /** 沙箱可用（fs 非空）的工厂：环境工具的注册条件成立，才谈得上被白名单排除 */
    private static ToolsetFactory factoryWithSandbox() {
        SandboxSession session = Mockito.mock(SandboxSession.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SandboxSession> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(session);
        // 注册期不触达 SSH：只按名字装配条目，桩会话足够
        return new ToolsetFactory(Mockito.mock(org.springframework.ai.chat.model.ChatModel.class),
                provider, "", "");
    }

    private static Set<String> toolNames(ToolRegistry registry) {
        return registry.entries().stream().map(entry -> entry.name).collect(Collectors.toSet());
    }

    @Test
    void orchestratorRegistryDropsEnvironmentTools() {
        ToolRegistry registry = factoryWithSandbox().build("chat-1", new ConversationState(),
                List.of(), GroupPrompts.ORCHESTRATOR_TOOLS,
                (type, description, prompt, background) -> null, null, null);

        Set<String> names = toolNames(registry);
        for (String envTool : List.of("Read", "Write", "Edit", "Bash", "Glob", "Grep")) {
            Assertions.assertFalse(names.contains(envTool),
                    "调度层不应注册环境工具 " + envTool + "，实际: " + names);
        }
    }

    @Test
    void orchestratorRegistryKeepsDispatchAndOutboundTools() {
        ToolRegistry registry = factoryWithSandbox().build("chat-1", new ConversationState(),
                List.of(), GroupPrompts.ORCHESTRATOR_TOOLS,
                (type, description, prompt, background) -> null, null, null);

        Set<String> names = toolNames(registry);
        Assertions.assertTrue(names.contains("Agent"), "分派工具必须保留: " + names);
        // 白名单里明确列出的其余能力（WebFetch/WebSearch/Todo*）保留
        Assertions.assertTrue(names.contains("WebFetch"), names.toString());
        Assertions.assertTrue(names.contains("WebSearch"), names.toString());
        Assertions.assertTrue(names.contains("TodoRead"), names.toString());
        Assertions.assertTrue(names.contains("TodoWrite"), names.toString());
    }

    @Test
    void defaultRegistryStillKeepsEnvironmentTools() {
        // 非群聊会话（白名单 null）不受影响：普通智能体仍拿全量环境工具
        ToolRegistry registry = factoryWithSandbox().build("chat-1", new ConversationState(),
                List.of(), null, null, null, null);

        Set<String> names = toolNames(registry);
        for (String envTool : List.of("Read", "Write", "Edit", "Bash", "Glob", "Grep")) {
            Assertions.assertTrue(names.contains(envTool), "普通会话仍需 " + envTool + ": " + names);
        }
    }

    @Test
    void readOnlyProfileCannotReachWriteTools() {
        // 白名单语义对 Read 收紧后，只读档案依然只拿 Read（不因默认注册而漏出写工具）
        ToolRegistry registry = factoryWithSandbox().build("chat-1", new ConversationState(),
                List.of(), List.of("Bash", "Glob", "Grep", "Read", "WebFetch", "WebSearch", "TodoWrite"),
                null, null, null);

        Set<String> names = toolNames(registry);
        Assertions.assertTrue(names.contains("Read"), names.toString());
        Assertions.assertFalse(names.contains("Write"), "只读档案不能拿到 Write: " + names);
        Assertions.assertFalse(names.contains("Edit"), "只读档案不能拿到 Edit: " + names);
    }

    @Test
    void orchestratorAgentToolDescriptionListsOnlyMembers() {
        // 默认描述会列出 general-purpose/Explore——群聊里这两个类型不存在，
        // 实测会让模型先试错再放弃委派。群聊描述必须只出现真实成员名。
        List<com.mio.ai.framework.zagent.subagent.MemberLauncher.MemberAgent> members = List.of(
                new com.mio.ai.framework.zagent.subagent.MemberLauncher.MemberAgent(
                        42L, "演示翻译官", "精通中英文互译", "你是翻译", null),
                new com.mio.ai.framework.zagent.subagent.MemberLauncher.MemberAgent(
                        43L, "演示代码审查", "Java/Python 审查", "你是审查员", null));

        String description = GroupPrompts.agentToolDescription(members);

        Assertions.assertTrue(description.contains("演示翻译官"), description);
        Assertions.assertTrue(description.contains("演示代码审查"), description);
        Assertions.assertFalse(description.contains("general-purpose"),
                "群聊描述不能出现不存在的内置档案: " + description);
        Assertions.assertFalse(description.contains("Explore"), description);
    }
}
