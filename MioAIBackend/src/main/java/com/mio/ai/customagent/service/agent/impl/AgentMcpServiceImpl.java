package com.mio.ai.customagent.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.agent.AgentMcpMapper;
import com.mio.ai.customagent.model.dto.agentmcp.AgentMcpAddRequest;
import com.mio.ai.customagent.model.entity.AgentMcp;
import com.mio.ai.customagent.service.agent.AgentMcpService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP关联服务实现类
 */
@Service
public class AgentMcpServiceImpl extends ServiceImpl<AgentMcpMapper, AgentMcp> implements AgentMcpService {

    @Override
    public Long addAgentMcp(AgentMcpAddRequest request) {
        if (request.getAgentId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        if (request.getMcpId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "MCP工具ID不能为空");
        }
        AgentMcp agentMcp = new AgentMcp();
        agentMcp.setAgentId(request.getAgentId());
        agentMcp.setMcpId(request.getMcpId());
        agentMcp.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        agentMcp.setConfigOverride(request.getConfigOverride());
        this.save(agentMcp);
        return agentMcp.getId();
    }

    @Override
    public boolean deleteAgentMcp(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "关联ID不能为空");
        }
        return this.removeById(id);
    }

    @Override
    public boolean deleteByAgentIdAndMcpId(Long agentId, Long mcpId) {
        if (agentId == null || mcpId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID和MCP工具ID不能为空");
        }
        LambdaQueryWrapper<AgentMcp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcp::getAgentId, agentId)
                .eq(AgentMcp::getMcpId, mcpId);
        return this.remove(wrapper);
    }

    @Override
    public List<Long> getMcpIdsByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentMcp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcp::getAgentId, agentId)
                .eq(AgentMcp::getEnabled, 1);
        List<AgentMcp> list = this.list(wrapper);
        return list.stream().map(AgentMcp::getMcpId).toList();
    }

    @Override
    public boolean deleteByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentMcp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcp::getAgentId, agentId);
        return this.remove(wrapper);
    }
}
