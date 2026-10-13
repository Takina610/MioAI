package com.mio.ai.framework.zagent.subagent;

import com.mio.ai.framework.zagent.tools.SandboxFs;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * 群聊成员启动器：把产品侧的智能体（群成员）映射为可委派的子代理。
 * 框架只认成员名与运行物料（提示词/工具/上下文），资源装配由产品侧实现——
 * Agent 工具的 subagent_type 命中成员名时走成员路径，未命中回落内置档案。
 */
public interface MemberLauncher {

    /**
     * 按模型给出的 subagent_type 解析群成员并装配其运行物料。
     *
     * @param taskPrompt 委派任务全文（成员知识库检索以其为查询）
     * @return null = 该类型不是群成员（回落内置档案）
     */
    MemberPlan prepare(String subagentType, String taskPrompt, SandboxFs fs);

    /** 群成员一次委派的运行物料：成员自己的提示词 / MCP 工具 / 知识库命中 / 技能清单 / 展示信息 */
    record MemberPlan(long memberId, String name, String description, String systemPrompt, String icon,
                      List<ToolCallback> mcpTools, String knowledgeContext, String skillsContext) {
    }
}
