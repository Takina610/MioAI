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

    /** 沙箱技能路径：skills/&lt;skillId&gt;/（SkillRuntime.materialize 的落盘约定） */
    private static final java.util.regex.Pattern SKILL_PATH_PATTERN =
            java.util.regex.Pattern.compile("skills/(\\d+)/");

    private final AgentMessageService agentMessageService;
    private final String chatId;
    private final Long userId;
    private final Long agentId;
    private final AtomicInteger seq = new AtomicInteger();

    // 当前连接：run 开始时由 MioBot 注入；断开时 sendTyped 静默失败，不影响执行与落库。
    // 断线重连时被 attachReplay 替换为新连接（回放缓冲后续播直播）
    private volatile SseEmitter emitter;

    // 重连回放缓冲：本轮全部信封事件（心跳除外）按 seq 留存，断线重连按 lastSeq 续播。
    // 超上限（病态巨轮）时清空并停用缓冲——重连方收到 replay_unavailable 回退落库还原
    private record BufferedEvent(int seq, Map<String, Object> fields) {
    }

    private static final int REPLAY_BUFFER_MAX_EVENTS = 20_000;

    private final List<BufferedEvent> replayBuffer = new ArrayList<>();
    private boolean replayDisabled = false;

    /** emit 与 attachReplay 共用锁：回放+换接在临界区内完成，不留丢事件的缝隙 */
    private final Object emitLock = new Object();

    // 展示持久化：与本轮 SSE 事件同构的内容块（文本/思考/工具）+ 最终清单快照
    private final List<Map<String, Object>> displayBlocks = new ArrayList<>();
    private List<Map<String, Object>> displayPlan;

    // 本轮用户附件（MioBot 注入）：随 user 行持久化为展示块
    private volatile List<com.mio.ai.bot.model.dto.AttachmentItem> userAttachments;

    // MCP 工具描述表（MioBot 注入）：tool_use 块附带描述，前端工具行展示名称与描述
    private volatile Map<String, String> mcpToolDesc = Map.of();

    // 技能名表（skillId → 名称，MioBot 注入）：工具读取 skills/<id>/ 文件时在工具行标注来源技能
    private volatile Map<String, String> skillNames = Map.of();

    // WIP 行（增量落库）：轮次开始即建行，每个模型步刷新 blocks，正常收尾删除。
    // 进程崩溃/被杀时该行保留已流出的部分过程，刷新页面与下一轮水合都拿得到
    private volatile Long wipRowId;

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

    /** MCP 工具描述表（注册名 → 描述，MioBot 构造时注入）：tool_use 行据此展示工具描述 */
    public void setMcpToolDesc(Map<String, String> mcpToolDesc) {
        this.mcpToolDesc = mcpToolDesc == null ? Map.of() : mcpToolDesc;
    }

    /** 技能名表（skillId → 名称，MioBot 构造时注入）：工具行据此标注技能来源 */
    public void setSkillNames(Map<String, String> skillNames) {
        this.skillNames = skillNames == null ? Map.of() : skillNames;
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
        String desc = mcpToolDesc.get(tool);
        if (desc != null && !desc.isBlank()) {
            block.put("desc", desc);
        }
        block.put("args", "");
        block.put("status", "running");
        displayBlocks.add(block);
        emit(SseChunk.toolUse(id, tool, desc).fields());
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
     * Agent 工具（子代理）例外：其结果是右侧子代理面板的内容主体
     * （最终报告 + agentId/usage 页脚），预览截断会让面板流式期间缺报告，故与落库同量级下发。
     */
    public void toolResult(String id, String tool, String content) {
        String full = content == null ? "" : content;
        if (full.length() > TOOL_RESULT_PERSIST_CAP) {
            full = full.substring(0, TOOL_RESULT_PERSIST_CAP)
                    + "\n[...result truncated at " + TOOL_RESULT_PERSIST_CAP + " chars...]";
        }
        String skill = completeDisplayTool(id, tool, full);
        int previewCap = "Agent".equals(tool) ? TOOL_RESULT_PERSIST_CAP : TOOL_RESULT_PREVIEW_LENGTH;
        String preview = full.length() > previewCap
                ? full.substring(0, previewCap) + "…" : full;
        emit(SseChunk.toolResult(id, tool, preview, skill).fields());
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

    /** 知识库检索摘要：追加 kb 展示块并推送事件（落库，刷新后仍可见，可展开看命中片段） */
    public void kbContext(List<Map<String, Object>> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "kb");
        block.put("groups", groups);
        displayBlocks.add(block);
        emit(SseChunk.kbContext(groups).fields());
    }

    /**
     * 知识库引用行（回合收尾调用）：检索是逐轮自动注入的，但摘要行只在回答实际引用了知识库文件时出现——
     * 按回答文本中出现的文件名过滤分组；引用行插到展示块最前（前端固定置顶渲染）。
     * 相关度分数无法可靠区分"真相关"与"弱相似噪声"（实测闲聊也能命中 0.7+），引用行为是更诚实的"使用"信号。
     */
    public void emitKbCitations(List<Map<String, Object>> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        StringBuilder answer = new StringBuilder();
        for (Map<String, Object> b : displayBlocks) {
            if ("text".equals(b.get("type")) && b.get("text") != null) {
                answer.append(b.get("text"));
            }
        }
        if (answer.isEmpty()) {
            return;
        }
        List<Map<String, Object>> cited = new ArrayList<>();
        for (Map<String, Object> group : groups) {
            Object rawHits = group.get("hits");
            if (!(rawHits instanceof List<?> hits) || hits.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> used = new ArrayList<>();
            for (Object raw : hits) {
                if (!(raw instanceof Map<?, ?> hit)) {
                    continue;
                }
                Object fileName = ((Map<String, Object>) hit).get("fileName");
                if (fileName != null && !String.valueOf(fileName).isBlank()
                        && answer.indexOf(String.valueOf(fileName)) >= 0) {
                    used.add((Map<String, Object>) hit);
                }
            }
            if (!used.isEmpty()) {
                Map<String, Object> citedGroup = new LinkedHashMap<>(group);
                citedGroup.put("hits", used);
                cited.add(citedGroup);
            }
        }
        if (cited.isEmpty()) {
            return;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "kb");
        block.put("groups", cited);
        displayBlocks.add(0, block);
        emit(SseChunk.kbContext(cited).fields());
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

    /** 系统提示（瞬态不落库）：本轮资源装配状态等用户须知，如 MCP 工具连接失败 */
    public void notice(String content) {
        emit(SseChunk.notice(content).fields());
    }

    public void usage(int inputTokens, int outputTokens, long durationMs) {
        emit(SseChunk.usage(inputTokens, outputTokens, durationMs).fields());
    }

    public void done() {
        emit(SseChunk.done().fields());
    }

    /**
     * 用户主动中断收尾：发 stopped 事件，并在展示块末尾追加中断标记
     * （随本轮持久化，刷新后仍显示"已停止"；水合器跳过未知块类型，不影响下一轮上下文）。
     * 须在 persistDisplay 之前调用。
     */
    public void stopped() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "stopped");
        displayBlocks.add(block);
        emit(SseChunk.stopped().fields());
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

    /** 轮次开始：创建 WIP 行（增量落库锚点）。游客不落库；失败降级为整轮末尾一次性落库 */
    public void beginAssistantWip() {
        if (agentMessageService == null || userId == null) {
            return;
        }
        try {
            AgentMessageDO row = new AgentMessageDO();
            row.setConversationId(chatId);
            row.setAgentId(agentId);
            row.setUserId(userId);
            row.setRole("assistant");
            row.setBlocks("[]");
            agentMessageService.append(row);
            wipRowId = row.getId();
        } catch (Exception e) {
            wipRowId = null;
            log.warn("WIP 行创建失败，本轮退化为末尾一次性落库: {}", e.getMessage());
        }
    }

    /** 模型步结束：把已流出的展示块刷进 WIP 行（崩溃最多丢最后一个进行中的步） */
    public void flushAssistantWip() {
        Long rowId = wipRowId;
        if (rowId == null) {
            return;
        }
        try {
            String planJson = displayPlan == null || displayPlan.isEmpty()
                    ? null : JacksonUtil.writeValueAsString(displayPlan);
            agentMessageService.updateDisplay(rowId, JacksonUtil.writeValueAsString(displayBlocks), planJson);
        } catch (Exception first) {
            try {
                String planJson = displayPlan == null || displayPlan.isEmpty()
                        ? null : JacksonUtil.writeValueAsString(displayPlan);
                agentMessageService.updateDisplay(rowId, JacksonUtil.writeValueAsString(displayBlocks), planJson);
            } catch (Exception second) {
                log.warn("WIP 行刷新失败（DB 抖动？）: {}", second.getMessage());
            }
        }
    }

    /**
     * 轮次收尾：最终展示行落库成功则删除 WIP 行（保持既有 seq 语义——compact 边界行
     * 必须排在 assistant 行之前才不会在水合时吞掉最终回复）；落库失败则保留 WIP 部分内容。
     */
    public void finishAssistantWip(boolean finalRowPersisted) {
        Long rowId = wipRowId;
        wipRowId = null;
        if (rowId == null) {
            return;
        }
        if (!finalRowPersisted) {
            flushAssistantWipWithId(rowId);
            return;
        }
        try {
            agentMessageService.deleteById(rowId);
        } catch (Exception e) {
            // 残留 WIP 行只造成"部分过程 + 完整过程"重复，可容忍；不回滚已落库的完整行
            log.warn("WIP 行清理失败（残留为部分过程行）: {}", e.getMessage());
        }
    }

    private void flushAssistantWipWithId(long rowId) {
        try {
            String planJson = displayPlan == null || displayPlan.isEmpty()
                    ? null : JacksonUtil.writeValueAsString(displayPlan);
            agentMessageService.updateDisplay(rowId, JacksonUtil.writeValueAsString(displayBlocks), planJson);
        } catch (Exception e) {
            log.warn("WIP 行收尾刷新失败: {}", e.getMessage());
        }
    }

    /** 展示持久化：完整 blocks/plan/duration 落 agent_message（游客不落库）。返回是否落库成功 */
    public boolean persistDisplay(String role, String text, Long durationMs) {
        return persistDisplay(role, text, durationMs, null);
    }

    public boolean persistDisplay(String role, String text, Long durationMs, Long groupSeq) {
        if (agentMessageService == null || userId == null) {
            return false;
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
            return true;
        } catch (Exception e) {
            log.warn("展示消息落库失败: {}", e.getMessage());
            return false;
        }
    }

    /** 已流出的工具调用参数（按 id 查工具块；无 id/子代理镜像的调用可能为 null） */
    String toolArgsOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (int i = displayBlocks.size() - 1; i >= 0; i--) {
            Map<String, Object> b = displayBlocks.get(i);
            if ("tool".equals(b.get("type")) && id.equals(b.get("id"))) {
                Object args = b.get("args");
                return args == null ? null : String.valueOf(args);
            }
        }
        return null;
    }

    private void emit(Map<String, Object> fields) {
        synchronized (emitLock) {
            int next = seq.incrementAndGet();
            if (!replayDisabled) {
                if (replayBuffer.size() >= REPLAY_BUFFER_MAX_EVENTS) {
                    replayBuffer.clear();
                    replayDisabled = true;
                    log.warn("回放缓冲超上限（{} 事件），本轮停用重连回放, chatId={}", REPLAY_BUFFER_MAX_EVENTS, chatId);
                } else if (!"heartbeat".equals(fields.get("type"))) {
                    replayBuffer.add(new BufferedEvent(next, fields));
                }
            }
            SseEmitter current = emitter;
            if (current != null) {
                SseStreams.sendTyped(current, fields, next);
            }
        }
    }

    /**
     * 断线重连接管（Claude/ChatGPT 式重连回放）：先把缓冲中 seq &gt; lastSeenSeq 的事件
     * 补送给新连接，再把后续直播切换过去。与 emit 同锁，回放与新事件之间无缝隙。
     * 被顶掉的旧连接立即回一条 superseded 并关闭——旧端不再空耗，也不会在
     * stall 后重连把 emitter 抢回去（多端/重复接管时 emitter 抖动）。
     */
    public void attachReplay(SseEmitter newEmitter, long lastSeenSeq) {
        SseEmitter superseded;
        synchronized (emitLock) {
            if (!replayDisabled) {
                for (BufferedEvent event : replayBuffer) {
                    if (event.seq() > lastSeenSeq) {
                        SseStreams.sendTyped(newEmitter, event.fields(), event.seq());
                    }
                }
            }
            superseded = emitter;
            emitter = newEmitter;
        }
        if (superseded != null && superseded != newEmitter) {
            // 不入缓冲不占 seq：纯单连接信令，接收端据此放弃本流
            SseStreams.sendTyped(superseded, SseChunk.content("superseded",
                    "连接已被新的接管请求替换").fields());
            try {
                superseded.complete();
            } catch (IllegalStateException ignored) {
                // 连接已被容器关闭
            }
        }
    }

    /** 重连回放是否已对本轮停用（缓冲超上限）：控制器据此直接回 replay_unavailable */
    public boolean isReplayDisabled() {
        return replayDisabled;
    }

    /** 配对收尾工具块；块参数涉及沙箱 skills/&lt;id&gt;/ 文件时标注来源技能（返回技能名，无则 null） */
    private String completeDisplayTool(String id, String tool, String result) {
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
                return markSkillUsage(b);
            }
        }
        // 没有配对的调用块（异常兜底）：补一个已完成块保证结果不丢
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool");
        block.put("tool", tool);
        block.put("result", result);
        block.put("status", "done");
        displayBlocks.add(block);
        return null;
    }

    /**
     * 技能使用检测：技能经系统提示清单按需暴露，模型用 Read/Bash 触达 skills/&lt;id&gt;/ 下的文件即视为使用该技能，
     * 工具行标注 skill 名（不做会话开头的"加载技能"行——是否用技能由模型按需决定）
     */
    private String markSkillUsage(Map<String, Object> block) {
        if (skillNames.isEmpty()) {
            return null;
        }
        String tool = String.valueOf(block.get("tool"));
        boolean pathAware = "Read".equals(tool) || "readFile".equals(tool)
                || "Bash".equals(tool) || "executeTerminalCommand".equals(tool) || "runCommand".equals(tool);
        if (!pathAware) {
            return null;
        }
        String args = String.valueOf(block.get("args"));
        java.util.regex.Matcher matcher = SKILL_PATH_PATTERN.matcher(args);
        while (matcher.find()) {
            String name = skillNames.get(matcher.group(1));
            if (name != null && !name.isBlank()) {
                block.put("skill", name);
                return name;
            }
        }
        return null;
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
