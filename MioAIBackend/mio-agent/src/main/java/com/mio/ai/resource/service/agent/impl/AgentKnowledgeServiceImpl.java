package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.mapper.agent.AgentKnowledgeMapper;
import com.mio.ai.resource.model.dto.agentknowledge.AgentKnowledgeAddRequest;
import com.mio.ai.resource.model.entity.AgentKnowledge;
import com.mio.ai.resource.service.agent.AgentKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联服务实现类
 */
@Service
public class AgentKnowledgeServiceImpl extends ServiceImpl<AgentKnowledgeMapper, AgentKnowledge> implements AgentKnowledgeService {

    @Override
    public Long addAgentKnowledge(AgentKnowledgeAddRequest request) {
        if (request.getAgentId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        if (request.getKbId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        AgentKnowledge agentKnowledge = new AgentKnowledge();
        agentKnowledge.setAgentId(request.getAgentId());
        agentKnowledge.setKbId(request.getKbId());
        agentKnowledge.setRetrievalConfig(request.getRetrievalConfig());
        agentKnowledge.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        this.save(agentKnowledge);
        return agentKnowledge.getId();
    }

    @Override
    public boolean deleteAgentKnowledge(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "关联ID不能为空");
        }
        return this.removeById(id);
    }

    @Override
    public boolean deleteByAgentIdAndKbId(Long agentId, Long kbId) {
        if (agentId == null || kbId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID和知识库ID不能为空");
        }
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getKbId, kbId);
        return this.remove(wrapper);
    }

    @Override
    public List<Long> getKbIdsByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getEnabled, 1);
        List<AgentKnowledge> list = this.list(wrapper);
        return list.stream().map(AgentKnowledge::getKbId).toList();
    }

    @Override
    public List<AgentKnowledge> getEnabledBindingsByAgentId(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId)
                .eq(AgentKnowledge::getEnabled, 1)
                .orderByAsc(AgentKnowledge::getId);
        return this.list(wrapper);
    }

    @Override
    public boolean deleteByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentKnowledge::getAgentId, agentId);
        return this.remove(wrapper);
    }
}
