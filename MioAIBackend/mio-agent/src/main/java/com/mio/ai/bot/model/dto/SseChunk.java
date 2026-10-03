package com.mio.ai.bot.model.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 统一消息信封：MioBot 的流式事件序列化为一行 JSON。
 *
 * type 取值：
 *  answer      正文增量        {delta}
 *  thinking    思考/推理增量    {delta}（推理模型；工具调用前的步骤说明也走此通道）
 *  tool_call   工具调用开始     {id, tool, args}
 *  tool_result 工具执行结果     {id, tool, content}
 *  plan        任务清单快照     {steps:[{index,description,status}]}
 *  usage       用量尾块        {inputTokens, outputTokens, durationMs}
 *  done        结束标记        {}
 *  error       错误            {content}
 *
 * tool_call / tool_result 携带同一 id 用于前后配对（端点未返回 id 时回退按顺序配对）。
 * 扩展新类型时前端对未知 type 会按过程步骤兜底展示，无需同步发版。
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

    public static SseChunk toolCall(String id, String tool, String args) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "tool_call");
        putIfNotBlank(map, "id", id);
        map.put("tool", tool);
        map.put("args", args);
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

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
