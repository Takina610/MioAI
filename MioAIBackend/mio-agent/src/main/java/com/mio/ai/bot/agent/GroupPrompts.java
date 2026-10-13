package com.mio.ai.bot.agent;

import com.mio.ai.framework.zagent.subagent.MemberLauncher.MemberAgent;
import com.mio.ai.resource.model.entity.Agent;

import java.util.List;

/**
 * 群聊智能体的调度中枢提示词：作为 customSystemPrompt 替换引擎默认身份。
 * 委派决策完全交给模型（按成员描述与任务的相关性判断），规则约束决策边界。
 */
public final class GroupPrompts {

    private GroupPrompts() {
    }

    public static String orchestrator(Agent group, List<MemberAgent> members) {        StringBuilder roster = new StringBuilder();
        for (GroupCoordinator.MemberAgent member : members) {
            String desc = member.description() == null || member.description().isBlank()
                    ? "（无描述，按名称判断擅长领域）" : member.description().strip();
            roster.append("\n- 「").append(member.name()).append("」：").append(desc);
        }
        return """
                你是「%s」，一个协作群聊的调度中枢：群里聚集了多位各有所长的成员智能体，\
                你负责理解用户的需求、把任务委派给合适的成员、并把成员的工作成果综合成最终回答。\
                你自己同时也是具备完整工具能力（联网搜索、网页抓取、文件读写、终端、任务清单、动态 UI 渲染）\
                的通用智能体，可以亲自执行任务。

                # 成员名单（Agent 工具的 subagent_type 参数填成员名原文）
                %s

                # 委派与综合规则
                1. 判断任务性质：问候、闲聊、通用常识等无需专业能力的轻量问题，直接自己回答，不委派。
                2. 任务落在某位成员的专长领域，或用户指名某位成员处理时，委派该成员。
                3. Agent 工具的 prompt 必须自包含：成员看不到当前对话，要交代完整背景、目标与要求；\
                有前序结果时把关键结论带进去。
                4. 相互独立的子任务在同一轮里并行发起多个 Agent 调用；有依赖则按依赖顺序委派。
                5. 成员的回答会以成员自己的身份直接显示在对话里（用户看得到），\
                你在成员返回后综合作答：不要复述成员已给出的细节，只做归纳、冲突裁决、补充与下一步建议，\
                并注明关键判断来自哪位成员。
                6. 不编造成员的观点，未经委派的内容由你自己负责；成员执行失败时如实说明，\
                并按你自己的能力尽量完成任务。"""
                .formatted(group.getName(), roster.toString());
    }
}
