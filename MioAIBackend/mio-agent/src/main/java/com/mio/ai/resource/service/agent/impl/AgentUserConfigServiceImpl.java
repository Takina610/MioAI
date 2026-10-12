package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.resource.mapper.agent.AgentMapper;
import com.mio.ai.resource.mapper.agent.AgentUserConfigMapper;
import com.mio.ai.resource.model.dto.agent.AgentUserConfigRequest;
import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.model.entity.AgentUserConfig;
import com.mio.ai.resource.model.vo.agent.AgentUserConfigVO;
import com.mio.ai.resource.service.agent.AgentUserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
        config.setOpeningMessage(blankToNull(request.getOpeningMessage()));
        config.setOpeningQuestions(toJson(request.getOpeningQuestions()));
        config.setTemperature(request.getTemperature());
        config.setIcon(blankToNull(request.getIcon()));
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
        vo.setOpeningMessage(config.getOpeningMessage());
        vo.setOpeningQuestions(parseQuestions(config.getOpeningQuestions()));
        vo.setTemperature(config.getTemperature());
        vo.setIcon(config.getIcon());
        return vo;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 推荐问题列表 → JSON 数字符串（去空白项，全空回退 null=无覆盖） */
    public static String toJsonQuestions(List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return null;
        }
        List<String> cleaned = questions.stream()
                .filter(q -> q != null && !q.isBlank())
                .map(String::strip)
                .toList();
        return cleaned.isEmpty() ? null : JacksonUtil.writeValueAsString(cleaned);
    }

    private static String toJson(List<String> questions) {
        return toJsonQuestions(questions);
    }

    /** 推荐问题JSON数组安全解析（历史脏数据/空值回退空列表） */
    public static List<String> parseQuestions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = JacksonUtil.readValue(json, List.class);
            return raw == null ? List.of()
                    : raw.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
