package com.mio.ai.framework.plan;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Arrays;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 任务清单管理工具（MioManus 专用，按实例注入，非 Spring Bean）
 * <p>供 LLM 显式地创建计划、更新步骤状态、查看当前计划。
 * 每个动作都有明确的返回值（渲染后的计划），模型能据此自我校验。
 */
public class PlanningTool {

    private final AgentPlan plan;

    public PlanningTool(AgentPlan plan) {
        this.plan = plan;
    }

    @Tool(description = "管理当前任务的执行计划。在开始复杂任务时必须先调用本工具创建任务清单，"
            + "把任务分解为有序步骤；每完成一个步骤后更新其状态。"
            + "action 取值：create=创建计划（steps 换行分隔）；"
            + "update=更新指定步骤状态（需要 stepIndex 与 status）；view=查看当前计划。"
            + "status 取值：not_started/in_progress/done/failed")
    public String managePlan(
            @ToolParam(description = "动作：create / update / view") String action,
            @ToolParam(required = false, description = "create 时为步骤列表（换行分隔），其他动作忽略")
            String steps,
            @ToolParam(required = false, description = "update 时为目标步骤序号（从1开始）")
            Integer stepIndex,
            @ToolParam(required = false, description = "update 时的新状态：not_started/in_progress/done/failed")
            String status) {
        if (action == null || action.isBlank()) {
            return "action 不能为空";
        }
        switch (action.trim().toLowerCase()) {
            case "create" -> {
                if (steps == null || steps.isBlank()) {
                    return "创建计划失败：steps 不能为空，请用换行分隔各个步骤";
                }
                List<String> stepList = Arrays.stream(steps.split("\\n"))
                        .map(String::trim)
                        .toList();
                plan.createPlan(stepList);
                return "已创建任务清单（" + plan.size() + " 个步骤）：\n" + plan.render();
            }
            case "update" -> {
                if (stepIndex == null) {
                    return "更新失败：缺少 stepIndex";
                }
                AgentPlan.StepStatus stepStatus = parseStatus(status);
                if (stepStatus == null) {
                    return "更新失败：status 必须是 not_started/in_progress/done/failed 之一";
                }
                boolean ok = plan.updateStatus(stepIndex, stepStatus);
                if (!ok) {
                    return "更新失败：步骤 " + stepIndex + " 不存在（当前共 " + plan.size() + " 步）";
                }
                return "步骤状态已更新：\n" + plan.render();
            }
            case "view" -> {
                return plan.isEmpty() ? "尚未创建任务清单" : "当前任务清单：\n" + plan.render();
            }
            default -> {
                return "未知 action：" + action + "（支持 create / update / view）";
            }
        }
    }

    private AgentPlan.StepStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.trim().toLowerCase()) {
            case "not_started", "notstarted", "pending" -> AgentPlan.StepStatus.NOT_STARTED;
            case "in_progress", "inprogress", "running" -> AgentPlan.StepStatus.IN_PROGRESS;
            case "done", "completed", "success" -> AgentPlan.StepStatus.DONE;
            case "failed", "error" -> AgentPlan.StepStatus.FAILED;
            default -> null;
        };
    }
}
