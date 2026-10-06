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

    /** SSE 工具结果事件的预览长度：完整结果已进入模型上下文，前端只需可读摘要 */
    private static final int TOOL_RESULT_PREVIEW_LENGTH = 400;

    /** 工具结果块的持久化上限：供水合重建请求历史（对齐 zcode 模型可见预算量级） */
    private static final int TOOL_RESULT_PERSIST_CAP = 24_000;

    /** 步中断时悬挂工具块的合成结果（与水合器 ConversationHydrator 的合成文案一致） */
    private static final String INTERRUPTED_TOOL_RESULT =
            "<tool_use_error>Interrupted before completion</tool_use_error>";

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

    // 本轮用户附件（MioBot 注入）：随 user 行持久化为展示块
    private volatile List<com.mio.ai.bot.model.dto.AttachmentItem> userAttachments;

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

    /** 本轮用户附件（run 开始时注入，user 行落库时转展示块） */
    public void setUserAttachments(List<com.mio.ai.bot.model.dto.AttachmentItem> items) {
        this.userAttachments = items;
    }

    /** Agent 产出文件：追加附件展示块并推送事件（前端渲染为可下载附件） */
    public void attachments(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        closeOpenThinking();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "attachments");
        block.put("side", "output");
        block.put("items", items);
        displayBlocks.add(block);
        emit(SseChunk.attachments(items).fields());
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

    /**
     * 工具结果：块内保留全量（供水合重建请求历史，超长截头），前端事件发预览。
     */
    public void toolResult(String id, String tool, String content) {
        String full = content == null ? "" : content;
        if (full.length() > TOOL_RESULT_PERSIST_CAP) {
            full = full.substring(0, TOOL_RESULT_PERSIST_CAP)
                    + "\n[...result truncated at " + TOOL_RESULT_PERSIST_CAP + " chars...]";
        }
        completeDisplayTool(id, tool, full);
        String preview = full.length() > TOOL_RESULT_PREVIEW_LENGTH
                ? full.substring(0, TOOL_RESULT_PREVIEW_LENGTH) + "…" : full;
        emit(SseChunk.toolResult(id, tool, preview).fields());
    }

    /**
     * 中断收尾：把未配对完成的 running 工具块标记为中断结果。
     * 兜底收束后调用——防止本轮结束时仍有悬挂的"执行中"工具块（前端转圈、落库后刷新仍显示执行中）。
     */
    public void abortRunningTools() {
        for (Map<String, Object> block : displayBlocks) {
            if (!"tool".equals(block.get("type")) || !"running".equals(block.get("status"))) {
                continue;
            }
            block.put("status", "done");
            block.put("result", INTERRUPTED_TOOL_RESULT);
            Object id = block.get("id");
            String idText = id instanceof String text && !text.isBlank() ? text : null;
            emit(SseChunk.toolResult(idText, String.valueOf(block.get("tool")),
                    INTERRUPTED_TOOL_RESULT).fields());
        }
    }

    /** 任务清单变化：推送结构化步骤列表，前端渲染为计划面板 */
    public void plan(List<Map<String, Object>> steps) {
        displayPlan = steps;
        emit(SseChunk.plan(steps).fields());
    }

    /** 向用户提问：追加问答展示块并推送事件（答题走 /bot/answer） */
    public void question(String id, List<Map<String, Object>> questions) {
        closeOpenThinking();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "question");
        block.put("id", id);
        block.put("status", "pending");
        block.put("questions", questions);
        displayBlocks.add(block);
        emit(SseChunk.question(id, questions).fields());
    }

    /** 用户已作答：锁定问答块并广播（多端同步） */
    public void questionAnswered(String id, List<Map<String, Object>> answers) {
        for (int i = displayBlocks.size() - 1; i >= 0; i--) {
            Map<String, Object> b = displayBlocks.get(i);
            if ("question".equals(b.get("type")) && id.equals(b.get("id"))) {
                b.put("status", "answered");
                b.put("answers", answers);
                break;
            }
        }
        emit(SseChunk.questionAnswered(id, answers).fields());
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

    /** 压缩边界行：水合时据此作废更早历史并以摘要续接（zcode compact boundary 对位） */
    public void persistCompact(String summary) {
        if (agentMessageService == null || userId == null) {
            return;
        }
        try {
            AgentMessageDO row = new AgentMessageDO();
            row.setConversationId(chatId);
            row.setAgentId(agentId);
            row.setUserId(userId);
            row.setRole("assistant");
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "compact");
            block.put("text", summary == null ? "" : summary);
            row.setBlocks(JacksonUtil.writeValueAsString(List.of(block)));
            agentMessageService.append(row);
        } catch (Exception e) {
            log.warn("压缩边界落库失败: {}", e.getMessage());
        }
    }

    /** 展示持久化：完整 blocks/plan/duration 落 agent_message（游客不落库） */
    public void persistDisplay(String role, String text, Long durationMs) {
        persistDisplay(role, text, durationMs, null);
    }

    public void persistDisplay(String role, String text, Long durationMs, Long groupSeq) {
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
                List<com.mio.ai.bot.model.dto.AttachmentItem> userItems = userAttachments;
                if ("user".equals(role) && userItems != null && !userItems.isEmpty()) {
                    List<Map<String, Object>> itemMaps = new ArrayList<>();
                    for (com.mio.ai.bot.model.dto.AttachmentItem item : userItems) {
                        itemMaps.add(item.toMap());
                    }
                    Map<String, Object> attachBlock = new LinkedHashMap<>();
                    attachBlock.put("type", "attachments");
                    attachBlock.put("side", "input");
                    attachBlock.put("items", itemMaps);
                    blocks.add(attachBlock);
                }
                row.setBlocks(JacksonUtil.writeValueAsString(blocks));
            } else {
                row.setBlocks(JacksonUtil.writeValueAsString(displayBlocks));
                if (displayPlan != null && !displayPlan.isEmpty()) {
                    row.setPlan(JacksonUtil.writeValueAsString(displayPlan));
                }
                row.setDurationMs(durationMs != null ? durationMs.intValue() : null);
            }
            if (groupSeq != null && "user".equals(role)) {
                row.setGroupSeq(groupSeq);
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
