package com.mio.ai.bot.agent;

import com.mio.ai.bot.service.BotResourceService;
import com.mio.ai.bot.service.SkillRuntime;
import com.mio.ai.framework.zagent.history.ConversationHydrator;
import com.mio.ai.framework.zagent.subagent.MemberLauncher;
import com.mio.ai.framework.zagent.tools.fs.SandboxFs;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 群聊调度协调器：把群成员名单接到 Agent 工具的 subagent_type 解析上。
 * 委派是运行期装配——成员以自己的提示词、MCP 工具、知识库检索（以委派任务为查询）、
 * 技能清单作为子代理运行；成员名单在轮次开始时重建，成员被删/易主后下一轮自然缺席。
 * <p>成员线程（Thread）：同一成员在本会话的历次发言按成员归档，委派时内联进它的
 * 系统提示——成员不必每次从零开始，群主也不必把前情反复复述。归档来源有两处：
 * 历史水合（跨轮）与本轮成员输出（同轮多次委派）。
 */
public final class GroupCoordinator implements MemberLauncher {

    /** 内联进成员提示的最近发言条数上限：只带最近几轮，避免成员上下文被旧内容占满 */
    private static final int THREAD_MAX_UTTERANCES = 3;
    /** 单条发言内联上限（字符）：成员长回答只保留开头结论部分 */
    private static final int THREAD_UTTERANCE_CAP = 1_500;
    /** 线程上下文总上限（字符） */
    private static final int THREAD_TOTAL_CAP = 4_000;

    private final List<MemberAgent> members;
    private final Long userId;
    private final BotResourceService resources;
    /** 成员ID → 本会话历次发言（可变：轮次内成员产出即时并入） */
    private final Map<Long, List<ConversationHydrator.MemberUtterance>> memberHistory =
            new LinkedHashMap<>();

    public GroupCoordinator(List<MemberAgent> members, Long userId, BotResourceService resources) {
        this.members = List.copyOf(members);
        this.userId = userId;
        this.resources = resources;
    }

    /** 历史水合结果并入成员线程（轮次开始时由 MioBot 注入） */
    public synchronized void mergeHistory(Map<Long, List<ConversationHydrator.MemberUtterance>> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        history.forEach((memberId, utterances) ->
                memberHistory.computeIfAbsent(memberId, key -> new ArrayList<>()).addAll(utterances));
    }

    /** 本轮成员名单（Agent 工具描述据此生成，只列真实可委派的成员） */
    public List<MemberAgent> members() {
        return members;
    }

    /** 本轮成员的产出即时并入线程：同一轮内再次委派同一成员时能接上自己刚说过的话 */
    public synchronized void recordUtterance(long memberId, String name, String text) {
        if (memberId <= 0 || text == null || text.isBlank()) {
            return;
        }
        memberHistory.computeIfAbsent(memberId, key -> new ArrayList<>())
                .add(new ConversationHydrator.MemberUtterance(memberId, name, text));
    }

    /** 成员在本会话的线程上下文（无发言返回 null） */
    private synchronized String threadContext(long memberId) {
        List<ConversationHydrator.MemberUtterance> utterances = memberHistory.get(memberId);
        if (utterances == null || utterances.isEmpty()) {
            return null;
        }
        // 从最新往前累积：超限时被丢掉的是更早的发言，而不是刚说过的结论
        List<String> picked = new ArrayList<>();
        int total = 0;
        int from = Math.max(0, utterances.size() - THREAD_MAX_UTTERANCES);
        for (int i = utterances.size() - 1; i >= from; i--) {
            String text = utterances.get(i).text();
            if (text.length() > THREAD_UTTERANCE_CAP) {
                text = text.substring(0, THREAD_UTTERANCE_CAP) + "…";
            }
            // 第一条无论多长都保留（否则只剩一句说明，等于没注入）
            if (!picked.isEmpty() && total + text.length() > THREAD_TOTAL_CAP) {
                break;
            }
            picked.add(0, text);
            total += text.length();
        }
        if (picked.isEmpty()) {
            return null;
        }
        return "以下是你在本会话中刚才的发言，回答时接续这些内容，不要重复已经说过的部分：\n"
                + String.join("\n\n", picked);
    }

    @Override
    public MemberAgent resolve(String subagentType) {
        if (subagentType == null || subagentType.isBlank()) {
            return null;
        }
        String token = subagentType.strip();
        for (MemberAgent member : members) {
            if (token.equals(member.name())) {
                return member;
            }
        }
        if (token.startsWith("agent-")) {
            try {
                long id = Long.parseLong(token.substring("agent-".length()));
                for (MemberAgent member : members) {
                    if (member.id() == id) {
                        return member;
                    }
                }
            } catch (NumberFormatException ignored) {
                // 兜底格式不合法按未命中处理
            }
        }
        return null;
    }

    @Override
    public MemberPlan prepare(MemberAgent member, String taskPrompt, SandboxFs fs) {
        List<ToolCallback> mcpTools = List.of(resources.assembleMcpTools(member.id()).callbacks());
        String knowledge = resources.buildKnowledge(member.id(), userId, taskPrompt, null, null).context();
        String skills = SkillRuntime.materialize(resources.assembleSkills(member.id()), fs);
        return new MemberPlan(member.id(), member.name(), member.description(), member.systemPrompt(),
                member.icon(), mcpTools, knowledge, skills, threadContext(member.id()));
    }
}
