package com.mio.ai.customagent.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.agentknowledge.AgentKnowledgeAddRequest;
import com.mio.ai.customagent.model.entity.AgentKnowledge;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联服务接口
 */
public interface AgentKnowledgeService extends IService<AgentKnowledge> {

    /**
     * 添加智能体-知识库关联
     */
    Long addAgentKnowledge(AgentKnowledgeAddRequest request);

    /**
     * 删除智能体-知识库关联
     */
    boolean deleteAgentKnowledge(Long id);

    /**
     * 根据智能体ID和知识库ID删除关联
     */
    boolean deleteByAgentIdAndKbId(Long agentId, Long kbId);

    /**
     * 根据智能体ID获取知识库列表
     */
    List<Long> getKbIdsByAgentId(Long agentId);

    /**
     * 获取智能体所有启用的知识库绑定（含每个绑定的检索配置）
     */
    List<AgentKnowledge> getEnabledBindingsByAgentId(Long agentId);

    /**
     * 根据智能体ID删除所有关联
     */
    boolean deleteByAgentId(Long agentId);
}
