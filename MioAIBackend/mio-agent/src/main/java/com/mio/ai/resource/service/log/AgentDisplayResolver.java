package com.mio.ai.resource.service.log;

import com.mio.ai.resource.model.entity.Agent;
import com.mio.ai.resource.service.agent.AgentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体展示信息解析器：分布图/排行榜/趋势图按 agentId 批量取真实名称、头像与 bot-icon 配置，
 * 删失或越权 ID 回退"智能体 {id}"——不再出现"智能体 + 数字"充当正常名称的展示。
 * 群聊智能体（type=2）带 isGroup 标记：用量图表只统计实际对话的智能体，群本体不进图表。
 */
@Component
public class AgentDisplayResolver {

    /** 群聊智能体类型值（agent.type） */
    public static final int TYPE_GROUP = 2;

    @Resource
    private AgentService agentService;

    /**
     * 展示名 + 头像 + bot-icon 配置 + 类型
     */
    public record Display(String name, String avatar, String icon, Integer type) {

        /** 是否群聊智能体（图表排除项） */
        public boolean isGroup() {
            return type != null && type == TYPE_GROUP;
        }
    }

    /** 回退展示（智能体已删除/越权） */
    public static Display fallback(Long agentId) {
        return new Display("智能体 " + agentId, null, null, null);
    }

    /**
     * 批量解析（一次 listByIds，调用方无需关心缓存）
     */
    public Map<Long, Display> resolve(Collection<Long> agentIds) {
        Map<Long, Display> result = new HashMap<>();
        if (agentIds == null || agentIds.isEmpty()) {
            return result;
        }
        Map<Long, Agent> agents = new HashMap<>();
        try {
            List<Agent> rows = agentService.listByIds(agentIds);
            for (Agent agent : rows) {
                agents.put(agent.getId(), agent);
            }
        } catch (Exception ignored) {
            // 查询失败时全部走回退名
        }
        for (Long id : agentIds) {
            Agent agent = agents.get(id);
            if (agent != null && agent.getName() != null && !agent.getName().isBlank()) {
                result.put(id, new Display(agent.getName(), agent.getAvatar(), agent.getIcon(), agent.getType()));
            } else {
                result.put(id, fallback(id));
            }
        }
        return result;
    }
}
