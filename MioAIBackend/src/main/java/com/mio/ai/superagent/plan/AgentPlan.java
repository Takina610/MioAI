package com.mio.ai.superagent.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 智能体显式任务清单（对标 Manus 的 plan 文件机制）
 * <p>MioManus 此前的"规划"完全依赖 system prompt 的隐性指令，没有可追踪的任务状态。
 * AgentPlan 把任务分解显式化为一份可读写的计划：每一步有描述与状态
 * （待开始/进行中/已完成/失败），模型通过 PlanningTool 工具维护，
 * 并在每一步思考前注入上下文，从而支持失败重试与步骤回溯。
 */
public class AgentPlan {

    public enum StepStatus {
        NOT_STARTED("待开始"),
        IN_PROGRESS("进行中"),
        DONE("已完成"),
        FAILED("失败");

        private final String desc;

        StepStatus(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    /**
     * 计划中的单个步骤
     */
    public record Step(int index, String description, StepStatus status) {
    }

    private final List<String> descriptions = new ArrayList<>();
    private final List<StepStatus> statuses = new ArrayList<>();

    public synchronized void createPlan(List<String> steps) {
        descriptions.clear();
        statuses.clear();
        if (steps != null) {
            for (String s : steps) {
                if (s != null && !s.isBlank()) {
                    descriptions.add(s.trim());
                    statuses.add(StepStatus.NOT_STARTED);
                }
            }
        }
    }

    public synchronized boolean isEmpty() {
        return descriptions.isEmpty();
    }

    public synchronized int size() {
        return descriptions.size();
    }

    public synchronized boolean updateStatus(int stepIndex, StepStatus status) {
        if (stepIndex < 1 || stepIndex > descriptions.size() || status == null) {
            return false;
        }
        statuses.set(stepIndex - 1, status);
        return true;
    }

    /**
     * @return 渲染后的任务清单（Markdown 复选框风格）；未创建计划时返回空串
     */
    public synchronized String render() {
        if (descriptions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < descriptions.size(); i++) {
            String mark = switch (statuses.get(i)) {
                case DONE -> "[x]";
                case IN_PROGRESS -> "[~]";
                case FAILED -> "[!]";
                case NOT_STARTED -> "[ ]";
            };
            sb.append(mark).append(" ").append(i + 1).append(". ")
                    .append(descriptions.get(i))
                    .append("（").append(statuses.get(i).getDesc()).append("）\n");
        }
        return sb.toString().stripTrailing();
    }

    public synchronized List<Step> snapshot() {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < descriptions.size(); i++) {
            steps.add(new Step(i + 1, descriptions.get(i), statuses.get(i)));
        }
        return steps;
    }
}
