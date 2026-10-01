package com.mio.ai.superagent.agent;

import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.customagent.service.log.ToolCallLogService;
import com.mio.ai.superagent.agent.config.ToolCallAgent;
import com.mio.ai.framework.plan.AgentPlan;
import com.mio.ai.framework.plan.PlanningTool;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * @author: Takina
 * @date: 2026/3/31 16:53
 * @description:
 */

/**
 * AI 超级智能体（拥有自主规划能力，可以直接使用）
 * <p>由调用方手动创建实例，不作为 Spring Bean 管理
 * <p>规划机制：对标 Manus 的 plan 文件，通过 {@link PlanningTool} 维护显式任务清单
 * （{@link AgentPlan}），清单在每一步思考前注入提示词，模型据此推进/重试/回溯步骤。
 */
public class MioManus extends ToolCallAgent {

    private final AgentPlan plan = new AgentPlan();

    public MioManus(ToolCallback[] commonTools,
                    ChatModel mioManusChatModel,
                    ChatMemory chatMemory) {
        super(commonTools);
        init(mioManusChatModel, chatMemory, commonTools);
    }

    public MioManus(ToolCallback[] commonTools,
                    ChatModel mioManusChatModel,
                    ChatMemory chatMemory,
                    AgentUsageLogService agentUsageLogService,
                    Long agentId,
                    Long userId,
                    Long conversationId) {
        this(commonTools, mioManusChatModel, chatMemory, agentUsageLogService, null, agentId, userId, conversationId);
    }

    public MioManus(ToolCallback[] commonTools,
                    ChatModel mioManusChatModel,
                    ChatMemory chatMemory,
                    AgentUsageLogService agentUsageLogService,
                    ToolCallLogService toolCallLogService,
                    Long agentId,
                    Long userId,
                    Long conversationId) {
        super(commonTools, agentUsageLogService, toolCallLogService, chatMemory, agentId, userId, conversationId);
        init(mioManusChatModel, chatMemory, commonTools);
    }

    private void init(ChatModel mioManusChatModel, ChatMemory chatMemory, ToolCallback[] commonTools) {
        this.setName("mioManus");
        String SYSTEM_PROMPT = """
                你是 MioManus，一个全能型 AI 助手，致力于解决用户提出的任何任务。
                你可以调用各种工具，高效完成复杂需求。

                严格遵守以下对话规则：
                1. 若检测到你正在重复输出相同的内容，立即停止回答，不再继续生成。
                2. 任何工具调用失败后，必须分析原因并尝试重试，不得直接放弃。
                3. 只有在确认所有子任务均已完成且成功后，才能调用 terminate 工具结束交互。

                显式规划机制：
                - 接到非平凡任务后，第一步必须调用 managePlan 工具（action=create）把任务分解为有序步骤；
                - 每完成/失败一个步骤，立即调用 managePlan（action=update）更新对应步骤状态；
                - 某步骤失败时，把该步骤标记为 failed，分析原因后补充新步骤重试，不得静默放弃。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，你可以分解问题，逐步使用不同工具来解决。
                使用每个工具后，清晰说明执行结果并建议下一步操作。

                任务完成前的强制检查清单（调用 terminate 前必须全部通过）：
                - [ ] 用户要求的每一个子任务都已执行完毕
                - [ ] 任务清单中所有步骤均已标记为 done（可用 managePlan action=view 确认）
                - [ ] 所有工具调用均返回成功结果（如生成文件则必须拿到有效链接）
                - [ ] 若有失败的步骤，已重试成功或已明确告知用户
                任何一项未通过，继续执行任务，禁止调用 terminate。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话模型与会话记忆
        this.setChatModel(mioManusChatModel);
        this.setChatMemory(chatMemory);
        // 注册本实例专属的规划工具
        this.setAvailableTools(concatTools(commonTools, createPlanningToolCallback()));
    }

    private ToolCallback createPlanningToolCallback() {
        return ToolCallbacks.from(new PlanningTool(plan))[0];
    }

    private ToolCallback[] concatTools(ToolCallback[] a, ToolCallback b) {
        ToolCallback[] result = new ToolCallback[(a == null ? 0 : a.length) + 1];
        if (a != null) {
            System.arraycopy(a, 0, result, 0, a.length);
        }
        result[result.length - 1] = b;
        return result;
    }

    /**
     * 在每一步思考前把当前任务清单注入提示词，让模型始终"看得见"计划进度
     */
    @Override
    public String getNextStepPrompt() {
        String base = super.getNextStepPrompt();
        String planText = plan.render();
        if (planText.isEmpty()) {
            return base + "\n\n当前尚未创建任务清单。如果任务包含多个步骤，请先调用 managePlan(action=create) 创建。";
        }
        return base + "\n\n【当前任务清单】\n" + planText
                + "\n请按清单推进：完成的步骤及时标记 done，正在做的标记 in_progress。";
    }

    public AgentPlan getPlan() {
        return plan;
    }
}
