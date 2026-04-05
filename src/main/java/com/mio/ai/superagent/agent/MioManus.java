package com.mio.ai.superagent.agent;

import com.mio.ai.superagent.agent.config.ToolCallAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/31 16:53
 * @description:
 */

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class MioManus extends ToolCallAgent {

    public MioManus(ToolCallback[] commonTools,
                    ChatClient mioManusChatClient) {
        super(commonTools);
        this.setName("mioManus");
        String SYSTEM_PROMPT = """
                你是 MioManus，一个全能型 AI 助手，致力于解决用户提出的任何任务。
                你可以调用各种工具，高效完成复杂需求。

                严格遵守以下对话规则：
                1. 若检测到你正在重复输出相同的内容，立即停止回答，不再继续生成。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，你可以分解问题，逐步使用不同工具来解决。
                使用每个工具后，清晰说明执行结果并建议下一步操作。
                如果需要在任何时候终止交互，请使用 `terminate` 工具/函数调用。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        this.setChatClient(mioManusChatClient);
    }
}
