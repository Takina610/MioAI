package com.mio.ai.customagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.agent.AgentAddRequest;
import com.mio.ai.customagent.model.dto.agent.AgentQueryRequest;
import com.mio.ai.customagent.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.customagent.model.entity.Agent;
import com.mio.ai.customagent.model.vo.AgentVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体服务接口
 */
public interface AgentService extends IService<Agent> {

    /**
     * 创建智能体
     */
    Long addAgent(AgentAddRequest request, Long userId);

    /**
     * 更新智能体
     */
    boolean updateAgent(AgentUpdateRequest request, Long userId);

    /**
     * 删除智能体
     */
    boolean deleteAgent(Long id, Long userId);

    /**
     * 根据ID获取智能体
     */
    AgentVO getAgentById(Long id);

    /**
     * 分页查询智能体
     */
    Page<AgentVO> queryAgents(AgentQueryRequest request);

    /**
     * 获取公开的智能体列表（广场）
     */
    Page<AgentVO> getPublicAgents(long current, long size);
}
