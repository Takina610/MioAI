package com.mio.ai.superagent.model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE 统一消息信封：所有智能体的流式事件都序列化为一行 JSON。
 *
 * type 取值：
 *  answer      正文增量        {delta}
 *  thinking    思考/推理增量    {delta}（流式推理模型）；MioManus 步骤思考为 {content}（整段）
 *  action      MioManus 步骤动作 {content}（整段）
 *  final       MioManus 最终回复 {content}（整段）
 *  tool_call   工具调用开始     {tool, args}
 *  tool_result 工具执行结果     {tool, content}
 *  usage       用量尾块        {inputTokens, outputTokens, durationMs}
 *  done        结束标记        {}
 *  error       错误            {content}
 *
 * 扩展新类型时前端对未知 type 会按思考过程步骤兜底展示，无需同步发版。
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

    public static SseChunk toolCall(String tool, String args) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "tool_call");
        map.put("tool", tool);
        map.put("args", args);
        return new SseChunk(map);
    }

    public static SseChunk toolResult(String tool, String content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "tool_result");
        map.put("tool", tool);
        map.put("content", content);
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
}
