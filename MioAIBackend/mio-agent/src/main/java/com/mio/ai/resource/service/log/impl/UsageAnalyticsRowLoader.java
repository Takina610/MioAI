package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.RagRetrievalLog;
import com.mio.ai.resource.model.entity.SkillUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.RagRetrievalLogService;
import com.mio.ai.resource.service.log.SkillUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 使用记录深度分析的数据加载：把 agent/knowledge/mcp/skill 四类日志统一成 UsageRow，
 * 并在一次查询内解析智能体、知识库与用户名。
 */
@Component
public class UsageAnalyticsRowLoader {

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @Resource
    private RagRetrievalLogService ragRetrievalLogService;

    @Resource
    private ToolCallLogService toolCallLogService;

    @Resource
    private SkillUsageLogService skillUsageLogService;

    @Resource
    private AgentDisplayResolver agentDisplayResolver;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private UserService userService;

    public LoadedRows load(String type, LocalDateTime startTime, LocalDateTime endTime,
                           Long userId, Long agentId, boolean withUserNames) {
        List<UsageRow> rows = switch (type) {
            case "knowledge" -> loadKnowledge(startTime, endTime, userId);
            case "mcp" -> loadMcp(startTime, endTime, userId);
            case "skill" -> loadSkill(startTime, endTime, userId);
            default -> loadAgent(startTime, endTime, userId, agentId);
        };
        return new LoadedRows(rows, withUserNames ? resolveUserNames(rows) : Map.of());
    }

    public record LoadedRows(List<UsageRow> rows, Map<Long, String> userNames) {
    }

    private List<UsageRow> loadAgent(LocalDateTime startTime, LocalDateTime endTime, Long userId, Long agentId) {
        LambdaQueryWrapper<AgentUsageLog> wrapper = new LambdaQueryWrapper<AgentUsageLog>()
                .ge(AgentUsageLog::getCreateTime, toDate(startTime))
                .le(AgentUsageLog::getCreateTime, toDate(endTime));
        if (userId != null) {
            wrapper.eq(AgentUsageLog::getUserId, userId);
        }
        if (agentId != null) {
            wrapper.eq(AgentUsageLog::getAgentId, agentId);
        }
        List<AgentUsageLog> logs = agentUsageLogService.list(wrapper);
        Map<Long, AgentDisplayResolver.Display> displays = resolveDisplays(
                logs.stream().map(AgentUsageLog::getAgentId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<UsageRow> rows = new ArrayList<>(logs.size());
        for (AgentUsageLog log : logs) {
            if (log.getCreateTime() == null) {
                continue;
            }
            AgentDisplayResolver.Display display = log.getAgentId() == null ? null : displays.get(log.getAgentId());
            // 群聊本体不是实际对话方，与既有分布/趋势口径一致地排除
            if (display != null && display.isGroup()) {
                continue;
            }
            long tokens = tokens(log.getInputTokens(), log.getOutputTokens());
            String model = blankTo(log.getModel(), "未知模型");
            rows.add(new UsageRow(
                    log.getUserId(),
                    log.getAgentId(),
                    agentName(log.getAgentId(), displays),
                    model,
                    model,
                    tokens,
                    tokens,
                    log.getCreateTime().getTime(),
                    Integer.valueOf(1).equals(log.getStatus()) ? 1 : 0,
                    log.getConversationId() != null ? String.valueOf(log.getConversationId()) : null
            ));
        }
        return rows;
    }

    private List<UsageRow> loadKnowledge(LocalDateTime startTime, LocalDateTime endTime, Long userId) {
        LambdaQueryWrapper<RagRetrievalLog> wrapper = new LambdaQueryWrapper<RagRetrievalLog>()
                .ge(RagRetrievalLog::getCreateTime, toDate(startTime))
                .le(RagRetrievalLog::getCreateTime, toDate(endTime));
        if (userId != null) {
            wrapper.eq(RagRetrievalLog::getUserId, userId);
        }
        List<RagRetrievalLog> logs = ragRetrievalLogService.list(wrapper);
        Map<Long, AgentDisplayResolver.Display> displays = resolveDisplays(
                logs.stream().map(RagRetrievalLog::getAgentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> kbNames = new HashMap<>();

        List<UsageRow> rows = new ArrayList<>(logs.size());
        for (RagRetrievalLog log : logs) {
            if (log.getCreateTime() == null) {
                continue;
            }
            rows.add(new UsageRow(
                    log.getUserId(),
                    log.getAgentId(),
                    agentName(log.getAgentId(), displays),
                    log.getModel(),
                    kbName(log.getKbId(), kbNames),
                    1,
                    log.getTopK() != null ? log.getTopK() : 0,
                    log.getCreateTime().getTime(),
                    1,
                    log.getConversationId()
            ));
        }
        return rows;
    }

    private List<UsageRow> loadMcp(LocalDateTime startTime, LocalDateTime endTime, Long userId) {
        LambdaQueryWrapper<ToolCallLog> wrapper = new LambdaQueryWrapper<ToolCallLog>()
                .ge(ToolCallLog::getCreateTime, toDate(startTime))
                .le(ToolCallLog::getCreateTime, toDate(endTime));
        if (userId != null) {
            wrapper.eq(ToolCallLog::getUserId, userId);
        }
        List<ToolCallLog> logs = toolCallLogService.list(wrapper);
        Map<Long, AgentDisplayResolver.Display> displays = resolveDisplays(
                logs.stream().map(ToolCallLog::getAgentId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<UsageRow> rows = new ArrayList<>(logs.size());
        for (ToolCallLog log : logs) {
            if (log.getCreateTime() == null) {
                continue;
            }
            String target = blankTo(log.getName(),
                    log.getToolId() != null ? "工具 " + log.getToolId() : "MCP 工具");
            rows.add(new UsageRow(
                    log.getUserId(),
                    log.getAgentId(),
                    agentName(log.getAgentId(), displays),
                    null,
                    target,
                    1,
                    log.getExecutionTime() != null ? log.getExecutionTime() : 0,
                    log.getCreateTime().getTime(),
                    Integer.valueOf(1).equals(log.getStatus()) ? 1 : 0,
                    log.getConversationId() != null ? String.valueOf(log.getConversationId()) : null
            ));
        }
        return rows;
    }

    private List<UsageRow> loadSkill(LocalDateTime startTime, LocalDateTime endTime, Long userId) {
        LambdaQueryWrapper<SkillUsageLog> wrapper = new LambdaQueryWrapper<SkillUsageLog>()
                .ge(SkillUsageLog::getCreateTime, toDate(startTime))
                .le(SkillUsageLog::getCreateTime, toDate(endTime));
        if (userId != null) {
            wrapper.eq(SkillUsageLog::getUserId, userId);
        }
        List<SkillUsageLog> logs = skillUsageLogService.list(wrapper);
        Map<Long, AgentDisplayResolver.Display> displays = resolveDisplays(
                logs.stream().map(SkillUsageLog::getAgentId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<UsageRow> rows = new ArrayList<>(logs.size());
        for (SkillUsageLog log : logs) {
            if (log.getCreateTime() == null) {
                continue;
            }
            String target = blankTo(log.getSkillName(),
                    log.getSkillId() != null ? "技能 " + log.getSkillId() : "技能");
            rows.add(new UsageRow(
                    log.getUserId(),
                    log.getAgentId(),
                    agentName(log.getAgentId(), displays),
                    null,
                    target,
                    1,
                    1,
                    log.getCreateTime().getTime(),
                    1,
                    log.getConversationId() != null ? String.valueOf(log.getConversationId()) : null
            ));
        }
        return rows;
    }

    private Map<Long, AgentDisplayResolver.Display> resolveDisplays(Set<Long> ids) {
        return ids.isEmpty() ? Map.of() : agentDisplayResolver.resolve(ids);
    }

    private Map<Long, String> resolveUserNames(List<UsageRow> rows) {
        Set<Long> ids = rows.stream().map(UsageRow::userId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, String> names = new HashMap<>();
            for (User user : userService.listByIds(ids)) {
                String name = blankTo(user.getUserName(), user.getUserAccount());
                names.put(user.getId(), blankTo(name, "用户 " + user.getId()));
            }
            return names;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String agentName(Long agentId, Map<Long, AgentDisplayResolver.Display> displays) {
        if (agentId == null) {
            return "未知智能体";
        }
        AgentDisplayResolver.Display display = displays.get(agentId);
        return display != null ? display.name() : "智能体 " + agentId;
    }

    private String kbName(Long kbId, Map<Long, String> cache) {
        if (kbId == null) {
            return "未知知识库";
        }
        return cache.computeIfAbsent(kbId, id -> {
            if (id == 0L) {
                return "系统内置知识库";
            }
            try {
                KnowledgeBaseVO kb = knowledgeBaseService.getKnowledgeBaseById(id);
                if (kb != null && kb.getName() != null && !kb.getName().isBlank()) {
                    return kb.getName();
                }
            } catch (Exception ignored) {
                // 知识库删除/越权时回退展示
            }
            return "知识库 " + id;
        });
    }

    private static long tokens(Integer input, Integer output) {
        return (input != null ? input : 0L) + (output != null ? output : 0L);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
