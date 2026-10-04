package com.mio.ai.framework.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 通用 Agent 循环（参考 opencode runLoop / pi agent-loop 的骨架，与 Web 层解耦）：
 * 组装上下文 → 流式调用模型 → 有工具调用则执行并回填 → 循环，直到模型给出无工具调用的最终回答。
 * <p>框架层只负责"循环纪律"：步数上限（最后一轮注入"禁用工具、立即总结"）、
 * 工具执行、token 统计；SSE 发射/持久化由上层监听者实现。
 * <p>由调用方按请求创建实例，不作为 Spring Bean 管理。
 */
public class AgentLoop {

    /** 达到步数上限前的最后一轮提示：禁止再调工具，强制输出总结（opencode 同款收尾） */
    private static final String STEP_LIMIT_HINT =
            "[system] 已达到单轮最大执行步数。本轮禁止再调用任何工具，请立即基于已获得的信息输出总结与后续建议。";

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    /** 循环事件监听：上层负责流式展示、持久化与用量日志 */
    public interface Listener {
        void thinkingDelta(String delta);

        void answerDelta(String delta);

        void toolUse(String id, String tool);

        void toolArgs(String id, String delta);

        /** 工具执行完成（result 为完整输出，展示截断由监听者决定） */
        void toolResult(String id, String tool, String result);

        /** 每轮模型响应后的用量/日志钩子 */
        void stepFinished(ChatResponse response, int step, List<AssistantMessage.ToolCall> toolCalls);
    }

    /** 循环结果：是否自然完成（模型主动收尾）与用量统计 */
    public record Result(boolean finished, int steps, long inputTokens, long outputTokens) {
    }

    /**
     * 执行一轮任务循环。
     *
     * @param messages     会话消息（含本轮用户消息；循环内会追加助手/工具消息，调用方据此持久化）
     * @param systemPrompt 系统提示词（不含工具结果上下文）
     * @param promptSuffix 每轮追加到系统提示词末尾的动态内容（如任务清单）
     */
    public Result run(ChatModel chatModel, List<Message> messages, String systemPrompt,
                      ToolCallback[] tools, String promptSuffix, int maxSteps, Listener listener) {
        long inputTokens = 0;
        long outputTokens = 0;
        boolean finished = false;
        int step;

        for (step = 1; step <= maxSteps && !finished; step++) {
            if (step == maxSteps) {
                messages.add(new UserMessage(STEP_LIMIT_HINT));
            }
            Prompt prompt = new Prompt(messages, buildOptions(chatModel, tools))
                    .augmentSystemMessage(systemPrompt + (promptSuffix == null ? "" : promptSuffix));

            StreamTurnCollector collector = new StreamTurnCollector(
                    listener::thinkingDelta, listener::answerDelta,
                    listener::toolUse, listener::toolArgs);
            chatModel.stream(prompt).doOnNext(collector::accept).blockLast();

            ChatResponse response = collector.build();
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                inputTokens += usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
                outputTokens += usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
            }

            AssistantMessage assistant = response.getResult().getOutput();
            listener.stepFinished(response, step, assistant.getToolCalls());

            // 模型不再调用工具 = 最终回答已流式输出完毕，任务结束
            if (!assistant.hasToolCalls()) {
                finished = true;
                break;
            }

            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(prompt, response);
            ToolResponseMessage toolResponseMessage =
                    (ToolResponseMessage) toolResult.conversationHistory()
                            .get(toolResult.conversationHistory().size() - 1);

            // 上下文回填：助手工具调用 + 工具结果（剔除 augment 注入的系统消息，避免逐轮堆积）
            messages.add(assistant);
            messages.add(toolResponseMessage);
            for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                listener.toolResult(toolResponse.id(), toolResponse.name(), toolResponse.responseData());
            }
        }
        return new Result(finished, step - 1, inputTokens, outputTokens);
    }

    /**
     * 以模型默认配置（yml 里的模型名）为基础挂上可用工具。
     * 必须用 OpenAiChatOptions：模型内部会强转 prompt.options，且裸 builder
     * 默认 model=gpt-5-mini，会覆盖 yml 里的 dashscope 模型名导致 404。
     */
    private OpenAiChatOptions buildOptions(ChatModel chatModel, ToolCallback[] tools) {
        if (chatModel.getDefaultOptions() instanceof OpenAiChatOptions defaults) {
            return defaults.mutate()
                    .toolCallbacks(List.of(tools))
                    .build();
        }
        return OpenAiChatOptions.builder()
                .toolCallbacks(List.of(tools))
                .build();
    }
}
