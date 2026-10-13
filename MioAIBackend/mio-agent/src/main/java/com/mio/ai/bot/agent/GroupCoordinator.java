package com.mio.ai.bot.agent;

import com.mio.ai.bot.service.BotResourceService;
import com.mio.ai.bot.service.SkillRuntime;
import com.mio.ai.framework.zagent.subagent.MemberLauncher;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 群聊调度协调器：把群成员名单接到 Agent 工具的 subagent_type 解析上。
 * 委派是运行期装配——成员以自己的提示词、MCP 工具、知识库检索（以委派任务为查询）、
 * 技能清单作为子代理运行；成员名单在轮次开始时重建，成员被删/易主后下一轮自然缺席。
 */
public final class GroupCoordinator implements MemberLauncher {

    private final List<MemberAgent> members;
    private final Long userId;
    private final BotResourceService resources;

    public GroupCoordinator(List<MemberAgent> members, Long userId, BotResourceService resources) {
        this.members = List.copyOf(members);
        this.userId = userId;
        this.resources = resources;
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
        String knowledge = resources.buildKnowledge(member.id(), userId, taskPrompt).context();
        String skills = SkillRuntime.materialize(resources.assembleSkills(member.id()), fs);
        return new MemberPlan(member.id(), member.name(), member.description(), member.systemPrompt(),
                member.icon(), mcpTools, knowledge, skills);
    }
}
