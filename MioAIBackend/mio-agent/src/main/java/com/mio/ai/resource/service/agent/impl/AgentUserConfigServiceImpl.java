package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.mapper.agent.AgentMapper;
import com.mio.ai.resource.mapper.agent.AgentUserConfigMapper;
import com.mio.ai.resource.model.dto.agent.AgentUserConfigRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentUserConfig;
import com.mio.ai.resource.model.vo.agent.AgentUserConfigVO;
import com.mio.ai.resource.service.agent.AgentUserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 内置智能体用户级个性化配置服务实现
 */
@Service
public class AgentUserConfigServiceImpl extends ServiceImpl<AgentUserConfigMapper, AgentUserConfig>
        implements AgentUserConfigService {

    @Autowired
    private AgentMapper agentMapper;

    @Override
    public AgentUserConfig getOverride(Long userId, Long agentId) {
        if (userId == null || agentId == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<AgentUserConfig>()
                .eq(AgentUserConfig::getUserId, userId)
                .eq(AgentUserConfig::getAgentId, agentId)
                .last("LIMIT 1"));
    }

    @Override
    public AgentUserConfigVO saveConfig(Long agentId, AgentUserConfigRequest request, Long userId) {
        Agent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        // 仅内置智能体（user_id 为空，如 MioBot）开放用户级个性化
        if (agent.getUserId() != null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅内置智能体支持个性化设置");
        }

        AgentUserConfig config = getOverride(userId, agentId);
        if (config == null) {
            config = new AgentUserConfig();
            config.setUserId(userId);
            config.setAgentId(agentId);
        }
        config.setSystemPrompt(blankToNull(request.getSystemPrompt()));
        config.setIcon(blankToNull(request.getIcon()));
        // null=保持不变：未带发布标记的调用不应意外下架
        if (request.getIsPublic() != null) {
            config.setIsPublic(request.getIsPublic());
        } else if (config.getIsPublic() == null) {
            config.setIsPublic(0);
        }
        this.saveOrUpdate(config);
        return toVO(config);
    }

    @Override
    public boolean resetConfig(Long agentId, Long userId) {
        AgentUserConfig config = getOverride(userId, agentId);
        if (config == null) {
            return true;
        }
        return this.removeById(config.getId());
    }

    private AgentUserConfigVO toVO(AgentUserConfig config) {
        AgentUserConfigVO vo = new AgentUserConfigVO();
        vo.setAgentId(config.getAgentId());
        vo.setSystemPrompt(config.getSystemPrompt());
        vo.setIcon(config.getIcon());
        vo.setIsPublic(config.getIsPublic());
        return vo;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
