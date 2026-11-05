package com.mio.ai.bot.agent;

import com.mio.ai.bot.service.BotResourceService;
import com.mio.ai.framework.zagent.history.ConversationHydrator;
import com.mio.ai.framework.zagent.subagent.MemberLauncher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

/**
 * 群聊成员线程（Thread）回归测试。
 * <p>成员每次委派都是全新上下文，若不复述前情就得由群主反复转述历史。
 * 这里验证：跨轮水合的历史与轮内产出都归入成员线程，并在下一次委派时内联进成员提示；
 * 截断上限保证长会话不会把成员上下文撑爆。
 */
class GroupCoordinatorThreadTest {

    private static BotResourceService stubResources() {
        BotResourceService resources = Mockito.mock(BotResourceService.class);
        Mockito.when(resources.assembleMcpTools(Mockito.anyLong()))
                .thenReturn(new BotResourceService.McpAssembly(new org.springframework.ai.tool.ToolCallback[0],
                        List.of()));
        Mockito.when(resources.buildKnowledge(Mockito.anyLong(), Mockito.any(), Mockito.anyString(),
                        Mockito.nullable(String.class), Mockito.nullable(String.class)))
                .thenReturn(BotResourceService.KnowledgeBuild.empty());
        Mockito.when(resources.assembleSkills(Mockito.anyLong())).thenReturn(List.of());
        return resources;
    }

    private static MemberLauncher.MemberAgent member(long id, String name) {
        return new MemberLauncher.MemberAgent(id, name, "擅长翻译", "你是一位翻译", null);
    }

    @Test
    void threadContextIsEmptyOnFirstDelegation() {
        GroupCoordinator coordinator = new GroupCoordinator(
                List.of(member(42L, "翻译官")), 7L, stubResources());

        MemberLauncher.MemberPlan plan = coordinator.prepare(coordinator.resolve("翻译官"), "翻译 hello", null);

        Assertions.assertNull(plan.threadContext(), "首个委派没有历史，不应注入线程上下文");
    }

    @Test
    void hydratedHistoryReachesNextDelegation() {
        GroupCoordinator coordinator = new GroupCoordinator(
                List.of(member(42L, "翻译官")), 7L, stubResources());
        coordinator.mergeHistory(Map.of(42L, List.of(
                new ConversationHydrator.MemberUtterance(42L, "翻译官", "上一次我译的是「早上好」"))));

        MemberLauncher.MemberPlan plan = coordinator.prepare(coordinator.resolve("翻译官"), "翻译 hello", null);

        Assertions.assertNotNull(plan.threadContext(), "上一轮的发言必须内联给成员");
        Assertions.assertTrue(plan.threadContext().contains("早上好"), plan.threadContext());
    }

    @Test
    void sameTurnUtteranceIsVisibleOnRetry() {
        GroupCoordinator coordinator = new GroupCoordinator(
                List.of(member(42L, "翻译官")), 7L, stubResources());
        coordinator.recordUtterance(42L, "翻译官", "我在本轮已经给出译文 A");

        MemberLauncher.MemberPlan plan = coordinator.prepare(coordinator.resolve("翻译官"), "再润色一版", null);

        Assertions.assertTrue(plan.threadContext().contains("译文 A"), plan.threadContext());
    }

    @Test
    void threadContextOnlyCoversTheAddressedMember() {
        GroupCoordinator coordinator = new GroupCoordinator(
                List.of(member(42L, "翻译官"), member(7L, "审查员")), 7L, stubResources());
        coordinator.recordUtterance(42L, "翻译官", "翻译官的旧发言");

        MemberLauncher.MemberPlan plan = coordinator.prepare(coordinator.resolve("审查员"), "审查一下", null);

        Assertions.assertNull(plan.threadContext(), "别的成员的历史不能串到当前成员身上");
    }

    @Test
    void longThreadContextIsCapped() {
        GroupCoordinator coordinator = new GroupCoordinator(
                List.of(member(42L, "翻译官")), 7L, stubResources());
        // 每条带唯一标记，验证只保留最近三条且总长封顶
        for (int i = 0; i < 6; i++) {
            coordinator.recordUtterance(42L, "翻译官", "第" + i + "轮：" + "长".repeat(3_000));
        }

        MemberLauncher.MemberPlan plan = coordinator.prepare(coordinator.resolve("翻译官"), "继续", null);

        Assertions.assertTrue(plan.threadContext().length() <= 4_500,
                "线程上下文必须有上限，实际长度: " + plan.threadContext().length());
        Assertions.assertTrue(plan.threadContext().contains("第5轮"), "最近一条必须在: " + plan.threadContext());
        Assertions.assertFalse(plan.threadContext().contains("第0轮"), "过旧的发言应被丢弃");
    }
}
