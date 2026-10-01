package com.mio.ai.superagent.plan;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 显式任务清单纯单元测试（不依赖任何外部环境）
 */
class AgentPlanTest {

    @Test
    void createAndRenderPlan() {
        AgentPlan plan = new AgentPlan();
        Assertions.assertTrue(plan.isEmpty());

        plan.createPlan(List.of("查询地点", "搜索图片", "生成PDF"));
        Assertions.assertEquals(3, plan.size());
        String rendered = plan.render();
        Assertions.assertTrue(rendered.contains("1. 查询地点"));
        Assertions.assertTrue(rendered.contains("待开始"));
        Assertions.assertFalse(rendered.contains("[x]"));
    }

    @Test
    void updateStepStatus() {
        AgentPlan plan = new AgentPlan();
        plan.createPlan(List.of("步骤A", "步骤B"));

        Assertions.assertTrue(plan.updateStatus(1, AgentPlan.StepStatus.DONE));
        Assertions.assertTrue(plan.updateStatus(2, AgentPlan.StepStatus.IN_PROGRESS));
        String rendered = plan.render();
        Assertions.assertTrue(rendered.contains("[x] 1. 步骤A"));
        Assertions.assertTrue(rendered.contains("[~] 2. 步骤B"));

        // 越界步骤更新失败
        Assertions.assertFalse(plan.updateStatus(0, AgentPlan.StepStatus.DONE));
        Assertions.assertFalse(plan.updateStatus(3, AgentPlan.StepStatus.DONE));
        Assertions.assertFalse(plan.updateStatus(1, null));
    }

    @Test
    void recreatePlanResetsState() {
        AgentPlan plan = new AgentPlan();
        plan.createPlan(List.of("旧步骤"));
        plan.updateStatus(1, AgentPlan.StepStatus.DONE);

        plan.createPlan(List.of("新步骤1", "新步骤2"));
        Assertions.assertEquals(2, plan.size());
        Assertions.assertEquals(AgentPlan.StepStatus.NOT_STARTED, plan.snapshot().get(0).status());
    }

    @Test
    void blankStepsAreSkipped() {
        AgentPlan plan = new AgentPlan();
        // 注意：Arrays.asList 才允许 null 元素
        plan.createPlan(java.util.Arrays.asList("有效步骤", "  ", "", null));
        Assertions.assertEquals(1, plan.size());
    }

    @Test
    void emptyPlanRendersEmptyString() {
        AgentPlan plan = new AgentPlan();
        Assertions.assertEquals("", plan.render());
        plan.createPlan(List.of());
        Assertions.assertEquals("", plan.render());
    }
}
