package com.mio.ai.customagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.agentmcp.AgentMcpAddRequest;
import com.mio.ai.customagent.model.entity.AgentMcp;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP关联服务接口
 */
public interface AgentMcpService extends IService<AgentMcp> {

    /**
     * 添加智能体-MCP关联
     */
    Long addAgentMcp(AgentMcpAddRequest request);

    /**
     * 删除智能体-MCP关联
     */
    boolean deleteAgentMcp(Long id);

    /**
     * 根据智能体ID获取MCP工具ID列表
     */
    List<Long> getMcpIdsByAgentId(Long agentId);

    /**
     * 根据智能体ID删除所有关联
     */
    boolean deleteByAgentId(Long agentId);
}
