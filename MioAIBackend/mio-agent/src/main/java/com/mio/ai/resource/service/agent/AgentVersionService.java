package com.mio.ai.resource.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentVersion;
import com.mio.ai.resource.model.vo.agent.AgentVersionVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 智能体发布版本快照服务
 */
public interface AgentVersionService extends IService<AgentVersion> {

    /**
     * 发布/回滚后生成新版本快照：版本号在上一版基础上递增（v1.0 → v1.1，逢 10 进位），
     * rollbackFrom 非空表示这是回滚产生的版本
     */
    AgentVersion createSnapshot(Agent agent, String bindingsJson, Long userId, String rollbackFrom);

    /**
     * 某智能体的版本列表（新版本在前），绑定资源名称已解析
     */
    List<AgentVersionVO> listVersions(Long agentId);
}
