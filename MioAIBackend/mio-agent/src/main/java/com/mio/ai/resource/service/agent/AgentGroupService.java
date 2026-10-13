package com.mio.ai.resource.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.agent.AgentGroupCreateRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentGroupMember;
import com.mio.ai.resource.model.vo.agent.AgentVO;

import java.util.List;

/**
 * 群聊智能体服务：群本体是 agent.type=2 的智能体行，成员关系在 agent_group_member。
 * 对话链路按普通智能体接入（/bot/chat?agentId=群id），调度中枢提示词由 bot 侧生成。
 */
public interface AgentGroupService extends IService<AgentGroupMember> {

    /** 创建群聊智能体（成员逐个校验归属与类型），返回群智能体ID */
    Long createGroup(AgentGroupCreateRequest request, Long userId);

    /** 编辑群聊（仅所有者）：改名 + 整体替换成员（至少保留 2 个） */
    boolean updateGroup(com.mio.ai.resource.model.dto.agent.AgentGroupUpdateRequest request, Long userId);

    /** 群成员智能体行（按加入顺序；已删除的成员自动滤除） */
    List<Agent> listMembers(Long groupAgentId);

    /** 群成员中当前对该用户仍可用的（成员被删或非本人所有则剔除） */
    List<Agent> listUsableMembers(Long groupAgentId, Long userId);

    /** 删除群的所有成员关系（解散群时级联） */
    void deleteByGroupId(Long groupAgentId);

    /** 把该智能体从所有群中移除（成员被删时级联） */
    void deleteByMemberId(Long memberAgentId);

    /** 给 type=2 的 VO 填充成员概要（id/名称/图标，堆叠群头像渲染用）；非群聊 VO 不动 */
    void attachMembers(AgentVO vo);

    /** 群成员概要列表（按加入顺序；空群/非群返回空表） */
    List<com.mio.ai.resource.model.vo.agent.AgentGroupMemberVO> buildMembers(Long groupAgentId);
}
