package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.AgentMapper;
import com.mio.ai.customagent.model.dto.agent.AgentAddRequest;
import com.mio.ai.customagent.model.dto.agent.AgentQueryRequest;
import com.mio.ai.customagent.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.customagent.model.entity.Agent;
import com.mio.ai.customagent.model.enums.AgentStatusEnum;
import com.mio.ai.customagent.model.enums.AgentTypeEnum;
import com.mio.ai.customagent.model.vo.AgentVO;
import com.mio.ai.customagent.service.AgentService;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体服务实现类
 */
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {

    @Override
    public Long addAgent(AgentAddRequest request, Long userId) {
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体名称不能为空");
        }
        Agent agent = new Agent();
        BeanUtil.copyProperties(request, agent);
        agent.setUserId(userId);
        agent.setStatus(AgentStatusEnum.DRAFT.getCode());
        agent.setUsageCount(0);
        agent.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : 0);
        this.save(agent);
        return agent.getId();
    }

    @Override
    public boolean updateAgent(AgentUpdateRequest request, Long userId) {
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(request.getId());
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该智能体");
        }
        BeanUtil.copyProperties(request, agent);
        return this.updateById(agent);
    }

    @Override
    public boolean deleteAgent(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (!agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该智能体");
        }
        return this.removeById(id);
    }

    @Override
    public AgentVO getAgentById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = this.getById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        return convertToVO(agent);
    }

    @Override
    public Page<AgentVO> queryAgents(AgentQueryRequest request) {
        Page<Agent> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(request.getName()), Agent::getName, request.getName())
                .eq(request.getType() != null, Agent::getType, request.getType())
                .eq(request.getStatus() != null, Agent::getStatus, request.getStatus())
                .eq(request.getIsPublic() != null, Agent::getIsPublic, request.getIsPublic())
                .eq(request.getUserId() != null, Agent::getUserId, request.getUserId())
                .orderByDesc(Agent::getCreateTime);
        Page<Agent> agentPage = this.page(page, wrapper);
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        voPage.setRecords(agentPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Page<AgentVO> getPublicAgents(long current, long size) {
        Page<Agent> page = new Page<>(current, size);
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Agent::getIsPublic, 1)
                .eq(Agent::getStatus, AgentStatusEnum.PUBLISHED.getCode())
                .orderByDesc(Agent::getCreateTime);
        Page<Agent> agentPage = this.page(page, wrapper);
        Page<AgentVO> voPage = new Page<>(agentPage.getCurrent(), agentPage.getSize(), agentPage.getTotal());
        voPage.setRecords(agentPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    private AgentVO convertToVO(Agent agent) {
        AgentVO vo = new AgentVO();
        BeanUtil.copyProperties(agent, vo);
        AgentTypeEnum typeEnum = AgentTypeEnum.getByCode(agent.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "未知");
        AgentStatusEnum statusEnum = AgentStatusEnum.getByCode(agent.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
