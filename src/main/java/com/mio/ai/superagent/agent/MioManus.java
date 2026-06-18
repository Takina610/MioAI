package com.mio.ai.superagent.agent;

import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.customagent.service.log.ToolCallLogService;
import com.mio.ai.superagent.agent.config.ToolCallAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

/**
 * @author: Takina
 * @date: 2026/3/31 16:53
 * @description:
 */

/**
 * AI 超级智能体（拥有自主规划能力，可以直接使用）
 * <p>由调用方手动创建实例，不作为 Spring Bean 管理
 */
public class MioManus extends ToolCallAgent {

    public MioManus(ToolCallback[] commonTools,
                    ChatClient mioManusChatClient) {
        super(commonTools);
        init(mioManusChatClient);
    }

    public MioManus(ToolCallback[] commonTools,
                    ChatClient mioManusChatClient,
                    AgentUsageLogService agentUsageLogService,
                    Long agentId,
                    Long userId,
                    Long conversationId) {
        this(commonTools, mioManusChatClient, agentUsageLogService, null, agentId, userId, conversationId);
    }

    public MioManus(ToolCallback[] commonTools,
                    ChatClient mioManusChatClient,
                    AgentUsageLogService agentUsageLogService,
                    ToolCallLogService toolCallLogService,
                    Long agentId,
                    Long userId,
                    Long conversationId) {
        super(commonTools, agentUsageLogService, toolCallLogService, agentId, userId, conversationId);
        init(mioManusChatClient);
    }

    private void init(ChatClient mioManusChatClient) {
        this.setName("mioManus");
        String SYSTEM_PROMPT = """
                你是 MioManus，一个全能型 AI 助手，致力于解决用户提出的任何任务。
                你可以调用各种工具，高效完成复杂需求。

                严格遵守以下对话规则：
                1. 若检测到你正在重复输出相同的内容，立即停止回答，不再继续生成。
                2. 任何工具调用失败后，必须分析原因并尝试重试，不得直接放弃。
                3. 只有在确认所有子任务均已完成且成功后，才能调用 terminate 工具结束交互。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，你可以分解问题，逐步使用不同工具来解决。
                使用每个工具后，清晰说明执行结果并建议下一步操作。

                任务完成前的强制检查清单（调用 terminate 前必须全部通过）：
                - [ ] 用户要求的每一个子任务都已执行完毕
                - [ ] 所有工具调用均返回成功结果（如生成文件则必须拿到有效链接）
                - [ ] 若有失败的步骤，已重试成功或已明确告知用户
                任何一项未通过，继续执行任务，禁止调用 terminate。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        this.setChatClient(mioManusChatClient);
    }
}
