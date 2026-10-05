package com.mio.ai.framework.zagent;

import com.mio.ai.framework.zagent.history.ConversationEntry;

import java.util.List;

/**
 * 引擎事件（zcode session events 的信封投影）：由上层翻译为 SSE 与持久化。
 */
public interface AgentEvents {

    void thinkingDelta(String delta);

    void answerDelta(String delta);

    void toolUse(String id, String name);

    void toolArgs(String id, String delta);

    /** 工具执行完成（content 为完整模型可见结果，展示截断由上层决定） */
    void toolResult(String id, String name, String content);

    /** 任务清单可能已变化（TodoWrite 后） */
    void todosChanged();

    /** 瞬态失败将自动重试（本轮尚无任何输出时无感重发） */
    void retryScheduled(int attempt, int maxAttempts, String reason);

    /** 一步模型请求完成（累计用量与该步工具调用，日志钩子） */
    void stepFinished(int step, long inputTokens, long outputTokens,
                      List<ConversationEntry.ToolCallInput> toolCalls);

    /** 上下文已压缩（summary 供落库边界与提示） */
    void compacted(String summary);

    /** 向用户提问（AskUserQuestion；questions 为渲染负载） */
    default void question(String id, java.util.List<java.util.Map<String, Object>> questions) {
    }

    /** 用户已作答（广播锁定问答 UI） */
    default void questionAnswered(String id, java.util.List<java.util.Map<String, Object>> answers) {
    }
}
