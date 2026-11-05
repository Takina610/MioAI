package com.mio.ai.bot.agent;

import com.mio.ai.framework.zagent.subagent.MemberLauncher.MemberAgent;
import com.mio.ai.resource.model.entity.agent.Agent;

import java.util.List;

/**
 * 群聊智能体的调度中枢提示词：作为 customSystemPrompt 替换引擎默认身份。
 * 委派决策完全交给模型（按成员描述与任务的相关性判断），规则约束决策边界。
 * <p>工具面与提示词一致地分层：调度中枢（Router）只保留委派、联网检索、任务清单
 * 与对外交互工具，环境类工具（读写文件/终端/搜索文件）整体摘掉——执行一律落在
 * 成员（Worker）身上，避免"主会话自己干活、被新消息不断打断"的失败模式。
 */
public final class GroupPrompts {

    /**
     * 调度中枢的工具白名单：只留分派与对外交互。
     * 白名单之外的环境工具（Read/Write/Edit/Bash/Glob/Grep）不注册——这是系统层面的
     * 边界，不依赖模型临场判断"这个任务该不该自己干"。
     */
    public static final List<String> ORCHESTRATOR_TOOLS = List.of(
            "Agent", "WebSearch", "WebFetch", "TodoRead", "TodoWrite",
            "TaskOutput", "TaskStop", "AskUserQuestion");

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
                你负责理解用户的需求、把任务委派给合适的成员、并把成员的工作成果综合成最终回答。

                # 成员名单（Agent 工具的 subagent_type 参数填成员名原文）
                %s

                # 委派与综合规则
                1. 判断任务性质：问候、闲聊、通用常识等轻量问题，直接自己回答，不委派。
                2. 其余任务一律委派给合适的成员。你没有文件读写、终端、文件搜索工具，\
                需要接触文件、代码、命令行、网页细节的工作只能由成员完成。
                3. 任务落在某位成员的专长领域，或用户指名某位成员处理时，委派该成员。
                4. Agent 工具的 prompt 必须自包含：成员看不到当前对话，要交代完整背景、目标与要求；\
                有前序结果时把关键结论带进去。
                5. 相互独立的子任务在同一轮里并行发起多个 Agent 调用；有依赖则按依赖顺序委派。
                6. 成员的回答会以成员自己的身份直接显示在对话里（用户看得到），\
                你在成员返回后综合作答：不要复述成员已给出的细节，只做归纳、冲突裁决、补充与下一步建议，\
                并注明关键判断来自哪位成员。
                7. 不编造成员的观点，未经委派的内容由你自己负责；成员执行失败时如实说明，\
                并按你自己的能力尽量完成任务。"""
                .formatted(group.getName(), roster.toString());
    }

    /**
     * 群聊里 Agent 工具的描述：可委派的只有成员，没有内置档案。
     * <p>沿用框架默认描述会让模型看到 general-purpose/Explore 这些这里并不存在的类型，
     * 实测会导致它先试错再放弃委派、改为自己硬做——正向描述成员名单才能稳定分派。
     */
    public static String agentToolDescription(List<MemberAgent> members) {
        StringBuilder roster = new StringBuilder();
        for (MemberAgent member : members) {
            String desc = member.description() == null || member.description().isBlank()
                    ? "" : " — " + member.description().strip();
            roster.append("\n- ").append(member.name()).append(desc);
        }
        return """
                Hand a piece of work to one of this group's member agents. Each member runs with its own \
                prompt, tools and knowledge base, and its answer is shown to the user under the member's name.

                Available subagent_type values (use the member name verbatim):
                %s

                ## When to use

                Delegate whenever the work needs a member's specialty, or when the user names a member. \
                Answer greetings, chit-chat and general knowledge yourself.

                - The member's final answer is returned to you as the tool result. Synthesize it (summarize, \
                resolve conflicts, add next steps) instead of repeating it — the user already saw it.
                - Members cannot see this conversation: write a self-contained prompt with all the background, \
                goal and requirements, including key findings from earlier steps.
                - Launch independent subtasks as multiple Agent calls in the same message so they run \
                concurrently; chain dependent ones in order.
                - `run_in_background: true` runs the member asynchronously; you'll be notified when it completes.""" 
                .formatted(roster.toString());
    }
}
