package com.mio.ai.resource.service.agent;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.agent.AgentUserConfigRequest;
import com.mio.ai.resource.model.entity.AgentUserConfig;
import com.mio.ai.resource.model.vo.agent.AgentUserConfigVO;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 内置智能体用户级个性化配置服务
 */
public interface AgentUserConfigService extends IService<AgentUserConfig> {

    /**
     * 查询用户对某内置智能体的个性化配置（无则返回 null，调用方回退内置默认）
     */
    AgentUserConfig getOverride(Long userId, Long agentId);

    /**
     * 保存（upsert）用户级配置；仅允许内置智能体，字段覆盖语义：null=清除该项覆盖
     */
    AgentUserConfigVO saveConfig(Long agentId, AgentUserConfigRequest request, Long userId);

    /**
     * 恢复默认：删除用户级配置行
     */
    boolean resetConfig(Long agentId, Long userId);
}
