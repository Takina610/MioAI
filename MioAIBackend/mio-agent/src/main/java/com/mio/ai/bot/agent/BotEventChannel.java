package com.mio.ai.bot.agent;

import com.mio.ai.bot.model.dto.SseChunk;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import com.mio.ai.bot.util.SseStreams;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 run 的事件通道：SSE 信封发送 + 展示块累积 + 展示持久化。
 * <p>与 {@link MioBot} 的循环逻辑解耦：循环只管"发生了什么"，
 * 这里负责"怎么发给前端、怎么落 agent_message"。
 */
@Slf4j
public class BotEventChannel {

    private final AgentMessageService agentMessageService;
    private final String chatId;
    private final Long userId;
    private final Long agentId;
    private final AtomicInteger seq = new AtomicInteger();

    // 当前连接：run 开始时由 MioBot 注入；断开时 sendTyped 静默失败，不影响执行与落库
    private volatile SseEmitter emitter;

    // 展示持久化：与本轮 SSE 事件同构的内容块（文本/思考/工具）+ 最终清单快照
    private final List<Map<String, Object>> displayBlocks = new ArrayList<>();
    private List<Map<String, Object>> displayPlan;

    public BotEventChannel(AgentMessageService agentMessageService,
                           String chatId, Long userId, Long agentId) {
        this.agentMessageService = agentMessageService;
        this.chatId = chatId;
        this.userId = userId;
        this.agentId = agentId;
    }

    public void bind(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void thinkingDelta(String delta) {
        appendDisplayText("thinking", delta);
        emit(SseChunk.delta("thinking", delta).fields());
    }

    public void answerDelta(String delta) {
        closeOpenThinking();
        appendDisplayText("text", delta);
        emit(SseChunk.delta("answer", delta).fields());
    }

    public void toolUse(String id, String tool) {
        closeOpenThinking();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool");
        if (id != null && !id.isBlank()) {
            block.put("id", id);
        }
        block.put("tool", tool);
        block.put("args", "");
        block.put("status", "running");
        displayBlocks.add(block);
        emit(SseChunk.toolUse(id, tool).fields());
    }

    public void toolArgs(String id, String delta) {
        // 优先按 id 配对；无 id 的兼容端点则落到最后一个 running 工具块
        Map<String, Object> target = null;
        if (id != null && !id.isBlank()) {
            for (int i = displayBlocks.size() - 1; i >= 0; i--) {
                Map<String, Object> b = displayBlocks.get(i);
                if ("tool".equals(b.get("type")) && id.equals(b.get("id"))) {
                    target = b;
                    break;
                }
            }
        }
        if (target == null) {
            for (int i = displayBlocks.size() - 1; i >= 0; i--) {
                Map<String, Object> b = displayBlocks.get(i);
                if ("tool".equals(b.get("type")) && "running".equals(b.get("status"))) {
                    target = b;
                    break;
                }
            }
        }
        if (target != null) {
            target.put("args", String.valueOf(target.get("args")) + delta);
        }
        emit(SseChunk.toolArgs(id, delta).fields());
    }

    public void toolResultPreview(String id, String tool, String preview) {
        completeDisplayTool(id, tool, preview);
        emit(SseChunk.toolResult(id, tool, preview).fields());
    }

    /** 任务清单变化：推送结构化步骤列表，前端渲染为计划面板 */
    public void plan(List<Map<String, Object>> steps) {
        displayPlan = steps;
        emit(SseChunk.plan(steps).fields());
    }

    public void heartbeat() {
        emit(SseChunk.heartbeat().fields());
    }

    /** 瞬态失败自动重试：只推状态不落展示块（重试成功后内容自然续上，用户无感） */
    public void retryScheduled(int attempt, int maxAttempts, String reason) {
        emit(SseChunk.retry(attempt, maxAttempts, reason).fields());
    }

    public void usage(int inputTokens, int outputTokens, long durationMs) {
        emit(SseChunk.usage(inputTokens, outputTokens, durationMs).fields());
    }

    public void done() {
        emit(SseChunk.done().fields());
    }

    public void error(String message) {
        emit(SseChunk.content("error", message).fields());
    }

    /** 展示持久化：完整 blocks/plan/duration 落 agent_message（游客不落库） */
    public void persistDisplay(String role, String text, Long durationMs) {
        if (agentMessageService == null || userId == null) {
            return;
        }
        closeOpenThinking();
        try {
            AgentMessageDO row = new AgentMessageDO();
            row.setConversationId(chatId);
            row.setAgentId(agentId);
            row.setUserId(userId);
            row.setRole(role);
            if (text != null) {
                List<Map<String, Object>> blocks = new ArrayList<>();
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "text");
                block.put("text", text);
                blocks.add(block);
                row.setBlocks(JacksonUtil.writeValueAsString(blocks));
            } else {
                row.setBlocks(JacksonUtil.writeValueAsString(displayBlocks));
                if (displayPlan != null && !displayPlan.isEmpty()) {
                    row.setPlan(JacksonUtil.writeValueAsString(displayPlan));
                }
                row.setDurationMs(durationMs != null ? durationMs.intValue() : null);
            }
            agentMessageService.append(row);
        } catch (Exception e) {
            log.warn("展示消息落库失败: {}", e.getMessage());
        }
    }

    private void emit(Map<String, Object> fields) {
        SseEmitter current = emitter;
        if (current != null) {
            SseStreams.sendTyped(current, fields, seq.incrementAndGet());
        }
    }

    private void completeDisplayTool(String id, String tool, String result) {
        for (int i = displayBlocks.size() - 1; i >= 0; i--) {
            Map<String, Object> b = displayBlocks.get(i);
            if (!"tool".equals(b.get("type")) || !"running".equals(b.get("status"))) {
                continue;
            }
            boolean idMatch = id != null && id.equals(b.get("id"));
            boolean nameMatch = tool != null && tool.equals(b.get("tool"));
            if (idMatch || nameMatch) {
                b.put("status", "done");
                b.put("result", result);
                return;
            }
        }
        // 没有配对的调用块（异常兜底）：补一个已完成块保证结果不丢
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool");
        block.put("tool", tool);
        block.put("result", result);
        block.put("status", "done");
        displayBlocks.add(block);
    }

    /** 展示块文本增量：合并同类末块（与前端渲染逻辑同构） */
    private void appendDisplayText(String type, String delta) {
        if (!displayBlocks.isEmpty()) {
            Map<String, Object> last = displayBlocks.get(displayBlocks.size() - 1);
            if (type.equals(last.get("type"))) {
                last.put("text", String.valueOf(last.get("text")) + delta);
                return;
            }
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put("text", delta);
        if ("thinking".equals(type)) {
            block.put("startMs", System.currentTimeMillis());
        }
        displayBlocks.add(block);
    }

    /** 思考块收尾：换块/落库时把进行中的思考块记上时长（前端展示"思考 · 持续了X秒"） */
    private void closeOpenThinking() {
        if (displayBlocks.isEmpty()) {
            return;
        }
        Map<String, Object> last = displayBlocks.get(displayBlocks.size() - 1);
        if ("thinking".equals(last.get("type")) && last.get("startMs") instanceof Long startMs) {
            last.put("durationMs", System.currentTimeMillis() - startMs);
            last.remove("startMs");
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
