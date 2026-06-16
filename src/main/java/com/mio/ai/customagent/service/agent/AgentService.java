package com.mio.ai.customagent.service.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.agent.AgentAddRequest;
import com.mio.ai.customagent.model.dto.agent.AgentQueryRequest;
import com.mio.ai.customagent.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.customagent.model.entity.Agent;
import com.mio.ai.customagent.model.vo.agent.AgentDetailVO;
import com.mio.ai.customagent.model.vo.agent.AgentVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体服务接口
 */
public interface AgentService extends IService<Agent> {

    Long addAgent(AgentAddRequest request, Long userId);

    boolean updateAgent(AgentUpdateRequest request, Long userId);

    boolean deleteAgent(Long id, Long userId);

    AgentVO getAgentById(Long id);

    AgentDetailVO getAgentDetailById(Long id, Long userId);

    Page<AgentVO> queryAgents(AgentQueryRequest request);

    Page<AgentVO> getPublicAgents(long current, long size);

    void publishAgent(Long agentId, AgentUpdateRequest request, Long userId);
}
