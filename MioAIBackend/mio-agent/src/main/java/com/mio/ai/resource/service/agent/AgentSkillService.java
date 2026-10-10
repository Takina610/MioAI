package com.mio.ai.resource.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.entity.AgentSkill;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 智能体-技能关联服务接口
 */
public interface AgentSkillService extends IService<AgentSkill> {

    /**
     * 绑定技能（同一智能体同一技能唯一）
     */
    Long addAgentSkill(AgentSkill binding);

    /**
     * 按 id 解绑
     */
    boolean deleteAgentSkill(Long id);

    /**
     * 按智能体+技能解绑
     */
    boolean deleteByAgentIdAndSkillId(Long agentId, Long skillId);

    /**
     * 智能体绑定的技能ID列表
     */
    List<Long> getSkillIdsByAgentId(Long agentId);

    /**
     * 删除智能体的全部技能绑定（删除智能体时级联）
     */
    boolean deleteByAgentId(Long agentId);

    /**
     * 删除技能侧的全部绑定（删除技能时级联）
     */
    boolean deleteBySkillId(Long skillId);
}
