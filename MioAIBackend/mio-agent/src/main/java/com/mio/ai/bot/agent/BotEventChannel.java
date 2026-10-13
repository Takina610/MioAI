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

    // 本轮已展示过技能行的技能（channel 与轮次同生命周期）：同一技能只展示一次，重复读取保持普通工具行
    private final java.util.Set<String> emittedSkills = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // 群聊会话（MioBot 注入）：Agent 工具行不下发——成员委派以 member 块（平等消息）呈现
    private volatile boolean suppressAgentToolRows;

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

    /** 群聊模式（MioBot 轮次开始时注入）：抑制 Agent 工具行，成员委派改走 member 块 */
    public void setSuppressAgentToolRows(boolean suppressAgentToolRows) {
        this.suppressAgentToolRows = suppressAgentToolRows;
    }

    /** Agent 产出文件：追加附件展示块并推送事件（前端渲染为可下载附件） */
    public synchronized void attachments(List<Map<String, Object>> items) {
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

    // displayBlocks 的并发约定：Agent 工具并行化后，成员/子代理镜像事件从并行池线程
    // 进入（member*/tool*），而 WIP 落库/收尾在轮次线程——所有块读写方法一律 synchronized
    // （锁 this，emit 另持 emitLock，获取顺序恒为 this→emitLock，无反向路径）。

    public synchronized void thinkingDelta(String delta) {
        appendDisplayText("thinking", delta);
        emit(SseChunk.delta("thinking", delta).fields());
    }

    public synchronized void answerDelta(String delta) {
        closeOpenThinking();
        appendDisplayText("text", delta);
        emit(SseChunk.delta("answer", delta).fields());
    }

    public synchronized void toolUse(String id, String tool) {
        // 群聊：Agent 工具行不呈现（成员委派由 member 块承载，成员与群主平等显示）
        if (suppressAgentToolRows && "Agent".equals(tool)) {
            return;
        }
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

    public synchronized void toolArgs(String id, String delta) {
        // 群聊：Agent 工具调用块整体不呈现（含参数流），成员委派由 member 块承载
        if (suppressAgentToolRows && isAgentToolCall(id)) {
            return;
        }
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

    /** 群聊下按 id 判断是否为 Agent 工具的参数流（其 tool_use 块已被抑制，无块可配对） */
    private boolean isAgentToolCall(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (Map<String, Object> b : displayBlocks) {
            if ("tool".equals(b.get("type")) && id.equals(b.get("id"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 工具结果：块内保留全量（供水合重建请求历史，超长截头），前端事件发预览。
     * Agent 工具（子代理）例外：其结果是右侧子代理面板的内容主体
     * （最终报告 + agentId/usage 页脚），预览截断会让面板流式期间缺报告，故与落库同量级下发。
     */
    public synchronized void toolResult(String id, String tool, String content) {
        // 群聊：Agent 工具结果不呈现（成员回答已在 member 块流式外流）
        if (suppressAgentToolRows && "Agent".equals(tool)) {
            return;
        }
        String full = content == null ? "" : content;
        if (full.length() > TOOL_RESULT_PERSIST_CAP) {
            full = full.substring(0, TOOL_RESULT_PERSIST_CAP)
                    + "\n[...result truncated at " + TOOL_RESULT_PERSIST_CAP + " chars...]";
        }
        String skill = completeDisplayTool(id, tool, full);
        int previewCap = "Agent".equals(tool) ? TOOL_RESULT_PERSIST_CAP : TOOL_RESULT_PREVIEW_LENGTH;
        String preview = full.length() > previewCap
                ? full.substring(0, previewCap) + "…" : full;
        // 技能读取：内容随事件全文下发（SKILL.md 都很小），前端把 Read 行升级为技能行
        emit(SseChunk.toolResult(id, tool, preview, skill,
                skill != null ? full : null).fields());
    }

    /**
     * 中断收尾：把未配对完成的 running 工具块标记为中断结果。
     * 兜底收束后调用——防止本轮结束时仍有悬挂的"执行中"工具块（前端转圈、落库后刷新仍显示执行中）。
     */
    public synchronized void abortRunningTools() {
        for (Map<String, Object> block : displayBlocks) {
            if ("tool".equals(block.get("type")) && !"running".equals(block.get("status"))) {
                continue;
            }
            if ("member".equals(block.get("type"))) {
                // 中断收尾：悬挂的成员消息也关闭（前端停止输入态）
                if ("running".equals(block.get("status"))) {
                    block.put("status", "done");
                    Object mid = block.get("id");
                    emit(SseChunk.memberDone(mid instanceof String text && !text.isBlank() ? text : null,
                            true).fields());
                }
                continue;
            }
            if (!"tool".equals(block.get("type"))) {
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

    // ---------- 群聊成员消息块（成员与群主平等显示，不经过子代理工具行） ----------

    /** 成员开始执行：追加 member 块（running，前端出"正在输入"态） */
    public synchronized void memberStart(String id, long memberId, String name, String icon) {
        closeOpenThinking();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "member");
        block.put("id", id);
        block.put("memberId", memberId);
        block.put("memberName", name);
        if (icon != null && !icon.isBlank()) {
            block.put("memberIcon", icon);
        }
        block.put("status", "running");
        block.put("text", "");
        displayBlocks.add(block);
        emit(SseChunk.memberStart(id, memberId, name, icon).fields());
    }

    /** 成员回答增量：按块 id 追加文本并下发事件 */
    public synchronized void memberDelta(String id, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        Map<String, Object> target = findMemberBlock(id);
        if (target != null) {
            target.put("text", String.valueOf(target.get("text")) + delta);
        }
        emit(SseChunk.memberDelta(id, delta).fields());
    }

    /** 成员结束：块状态收尾（failed=true 时前端按失败呈现） */
    public synchronized void memberDone(String id, boolean failed) {
        Map<String, Object> target = findMemberBlock(id);
        if (target != null) {
            target.put("status", "done");
            if (failed) {
                target.put("failed", true);
            }
        }
        emit(SseChunk.memberDone(id, failed).fields());
    }

    /** 按块 id 找成员块 */
    private Map<String, Object> findMemberBlock(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (int i = displayBlocks.size() - 1; i >= 0; i--) {
            Map<String, Object> b = displayBlocks.get(i);
            if ("member".equals(b.get("type")) && id.equals(b.get("id"))) {
                return b;
            }
        }
        return null;
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
     * 动态 UI 块（render_ui 工具产出，入参已过语义校验）：追加展示块并推送事件。
     * 块 id 会话内全局唯一（uuid 片段，跨轮次不撞），供 /bot/ui-state 交互寻址与
     * render_ui targetId 原位更新寻址；返回分配的 id（工具回执带 id，模型后续可更新）。
     * 水合器只回放 text/tool 块——UI 数据进模型上下文走 render_ui 的工具调用参数，不双算。
     */
    public synchronized String uiBlock(Map<String, Object> payload) {
        closeOpenThinking();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "ui");
        block.put("id", "ui-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        block.put("v", 1);
        block.putAll(payload);
        displayBlocks.add(block);
        emit(SseChunk.uiBlock(block).fields());
        return String.valueOf(block.get("id"));
    }

    /**
     * 原位替换既有 UI 块（render_ui targetId）：块 id 保持不变，事件带 update 标记
     * （前端跨消息按 id 替换，历史重载读到已更新版本）。先查本轮 displayBlocks
     * （同轮调整，随 WIP 增量落库）；再查历史行——定位后整行写回（与 /bot/ui-state
     * 同款路径），不新增消息。找不到目标返回 false（回执告知模型改为新建）。
     */
    public synchronized boolean uiBlockReplace(String targetId, Map<String, Object> payload) {
        closeOpenThinking();
        for (int i = 0; i < displayBlocks.size(); i++) {
            Map<String, Object> b = displayBlocks.get(i);
            if ("ui".equals(b.get("type")) && targetId.equals(b.get("id"))) {
                Map<String, Object> updated = replaceBlock(targetId, payload);
                displayBlocks.set(i, updated);
                emit(SseChunk.uiBlock(updated).fields());
                return true;
            }
        }
        if (agentMessageService == null || userId == null) {
            return false;
        }
        try {
            for (AgentMessageDO row : agentMessageService.listByConversation(chatId)) {
                List<Map<String, Object>> blocks = parseBlocks(row.getBlocks());
                if (blocks == null) {
                    continue;
                }
                for (int i = 0; i < blocks.size(); i++) {
                    Map<String, Object> b = blocks.get(i);
                    if (!"ui".equals(b.get("type")) || !targetId.equals(b.get("id"))) {
                        continue;
                    }
                    blocks.set(i, replaceBlock(targetId, payload));
                    agentMessageService.updateDisplay(row.getId(),
                            JacksonUtil.writeValueAsString(blocks), row.getPlan());
                    emit(SseChunk.uiBlock(blocks.get(i)).fields());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("UI 块原位替换失败, chatId={}, blockId={}: {}", chatId, targetId, e.getMessage());
        }
        return false;
    }

    private static Map<String, Object> replaceBlock(String targetId, Map<String, Object> payload) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "ui");
        block.put("id", targetId);
        block.put("v", 1);
        block.put("update", true);
        block.putAll(payload);
        return block;
    }

    /** 展示块 JSON 解析（畸形行返回 null 跳过） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseBlocks(String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return null;
        }
        try {
            return JacksonUtil.readValue(blocksJson, List.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 会话 id（render_ui 图片块 URL 登记簿核对等工具侧用途） */
    public String chatId() {
        return chatId;
    }

    /**
     * 知识库引用行（回合收尾调用）：检索是逐轮自动注入的，但摘要行只在回答实际引用了知识库文件时出现——
     * 按回答文本中出现的文件名过滤分组；引用行插到展示块最前（前端固定置顶渲染）。
     * 相关度分数无法可靠区分"真相关"与"弱相似噪声"（实测闲聊也能命中 0.7+），引用行为是更诚实的"使用"信号。
     */
    public synchronized void emitKbCitations(List<Map<String, Object>> groups) {
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
    public synchronized void question(String id, List<Map<String, Object>> questions) {
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
    public synchronized void questionAnswered(String id, List<Map<String, Object>> answers) {
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
    public synchronized void stopped() {
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
    public synchronized void flushAssistantWip() {
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

    private synchronized void flushAssistantWipWithId(long rowId) {
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

    public synchronized boolean persistDisplay(String role, String text, Long durationMs, Long groupSeq) {
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
    synchronized String toolArgsOf(String id) {
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

    /** 配对收尾工具块；Read 命中沙箱 skills/&lt;id&gt;/SKILL.md 时把该块升级为技能行（返回技能名，无则 null） */
    private synchronized String completeDisplayTool(String id, String tool, String result) {
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
                return upgradeToSkillRow(b, result);
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
     * 技能使用检测（zcode 语义）：技能经系统提示清单按需暴露，模型用 Read 读 skills/&lt;id&gt;/ 下的文件
     * 即为使用该技能——把该 Read 工具行升级为「技能 &lt;名&gt;」行（展开看 SKILL.md 内容）。
     * 同一技能本轮只升级第一次读取，后续重读保持普通工具行；是否用技能由模型按需决定，
     * 不做会话开头的"加载技能"行
     */
    private String upgradeToSkillRow(Map<String, Object> block, String content) {
        if (skillNames.isEmpty()) {
            return null;
        }
        String tool = String.valueOf(block.get("tool"));
        if (!"Read".equals(tool) && !"readFile".equals(tool)) {
            return null;
        }
        String args = String.valueOf(block.get("args"));
        java.util.regex.Matcher matcher = SKILL_PATH_PATTERN.matcher(args);
        while (matcher.find()) {
            String name = skillNames.get(matcher.group(1));
            if (name != null && !name.isBlank() && emittedSkills.add(name)) {
                block.clear();
                block.put("type", "skill_use");
                block.put("name", name);
                block.put("content", content);
                return name;
            }
        }
        return null;
    }

    /** 展示块文本增量：合并同类末块（与前端渲染逻辑同构） */
    private synchronized void appendDisplayText(String type, String delta) {
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
    private synchronized void closeOpenThinking() {
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
