package com.mio.ai.bot.model.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 统一消息信封：MioBot 引擎的流式事件序列化为一行 JSON（ZCode 风格的块流）。
 *
 * type 取值：
 *  answer      正文增量        {delta}
 *  thinking    思考/推理增量    {delta}
 *  tool_use    工具调用出现     {id, tool}（参数开始流式输出）
 *  tool_args   工具参数增量     {id, delta}
 *  tool_result 工具执行结果     {id, tool, content}
 *  plan        任务清单快照     {steps:[{index,description,status}]}
 *  heartbeat   保活心跳        {}（长工具执行期间维持连接，前端忽略内容仅重挂看门狗）
 *  retry       瞬态失败自动重试 {attempt, maxAttempts, reason}（本轮尚无任何输出时无感重发，前端提示后自行恢复）
 *  usage       用量尾块        {inputTokens, outputTokens, durationMs}
 *  done        结束标记        {}
 *  error       错误            {content}
 *
 * tool_use / tool_args / tool_result 携带同一 id 配对（端点未返回 id 时回退按顺序配对）。
 */
public record SseChunk(Map<String, Object> fields) {

    public static SseChunk delta(String type, String delta) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("delta", delta);
        return new SseChunk(map);
    }

    public static SseChunk content(String type, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        map.put("content", content);
        return new SseChunk(map);
    }

    public static SseChunk toolUse(String id, String tool) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "tool_use");
        putIfNotBlank(map, "id", id);
        map.put("tool", tool);
        return new SseChunk(map);
    }

    public static SseChunk toolArgs(String id, String delta) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "tool_args");
        putIfNotBlank(map, "id", id);
        map.put("delta", delta);
        return new SseChunk(map);
    }

    public static SseChunk toolResult(String id, String tool, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "tool_result");
        putIfNotBlank(map, "id", id);
        map.put("tool", tool);
        map.put("content", content);
        return new SseChunk(map);
    }

    public static SseChunk heartbeat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "heartbeat");
        return new SseChunk(map);
    }

    public static SseChunk retry(int attempt, int maxAttempts, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "retry");
        map.put("attempt", attempt);
        map.put("maxAttempts", maxAttempts);
        putIfNotBlank(map, "reason", reason);
        return new SseChunk(map);
    }

    public static SseChunk plan(List<Map<String, Object>> steps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "plan");
        map.put("steps", steps);
        return new SseChunk(map);
    }

    public static SseChunk usage(int inputTokens, int outputTokens, long durationMs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "usage");
        map.put("inputTokens", inputTokens);
        map.put("outputTokens", outputTokens);
        map.put("durationMs", durationMs);
        return new SseChunk(map);
    }

    public static SseChunk done() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "done");
        return new SseChunk(map);
    }

    /** 向用户提问（AskUserQuestion）：questions 为渲染负载 [{question, header, multiSelect, options[{label,description,preview?}]}] */
    public static SseChunk question(String id, List<Map<String, Object>> questions) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "question");
        map.put("id", id);
        map.put("status", "pending");
        map.put("questions", questions);
        return new SseChunk(map);
    }

    /** 用户已作答（广播锁定问答 UI）：answers = [{index, selections[]}] */
    public static SseChunk questionAnswered(String id, List<Map<String, Object>> answers) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "question");
        map.put("id", id);
        map.put("status", "answered");
        map.put("answers", answers);
        return new SseChunk(map);
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
