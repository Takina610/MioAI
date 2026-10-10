package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.resource.mapper.agent.AgentSkillMapper;
import com.mio.ai.resource.model.entity.AgentSkill;
import com.mio.ai.resource.service.agent.AgentSkillService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 智能体-技能关联服务实现
 */
@Service
public class AgentSkillServiceImpl extends ServiceImpl<AgentSkillMapper, AgentSkill> implements AgentSkillService {

    @Override
    public Long addAgentSkill(AgentSkill binding) {
        // 同一智能体同一技能只保留一条绑定
        AgentSkill exist = this.getOne(new LambdaQueryWrapper<AgentSkill>()
                .eq(AgentSkill::getAgentId, binding.getAgentId())
                .eq(AgentSkill::getSkillId, binding.getSkillId()));
        if (exist != null) {
            if (binding.getEnabled() != null) {
                exist.setEnabled(binding.getEnabled());
                this.updateById(exist);
            }
            return exist.getId();
        }
        this.save(binding);
        return binding.getId();
    }

    @Override
    public boolean deleteAgentSkill(Long id) {
        return this.removeById(id);
    }

    @Override
    public boolean deleteByAgentIdAndSkillId(Long agentId, Long skillId) {
        return this.remove(new LambdaQueryWrapper<AgentSkill>()
                .eq(AgentSkill::getAgentId, agentId)
                .eq(AgentSkill::getSkillId, skillId));
    }

    @Override
    public List<Long> getSkillIdsByAgentId(Long agentId) {
        return this.list(new LambdaQueryWrapper<AgentSkill>().eq(AgentSkill::getAgentId, agentId))
                .stream().map(AgentSkill::getSkillId).toList();
    }

    @Override
    public boolean deleteByAgentId(Long agentId) {
        return this.remove(new LambdaQueryWrapper<AgentSkill>().eq(AgentSkill::getAgentId, agentId));
    }

    @Override
    public boolean deleteBySkillId(Long skillId) {
        return this.remove(new LambdaQueryWrapper<AgentSkill>().eq(AgentSkill::getSkillId, skillId));
    }
}
