package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.RagRetrievalLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.model.vo.log.UsageStatsVO;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.RagRetrievalLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.resource.service.log.UsageStatsService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 使用记录统计服务实现类
 */
@Service
public class UsageStatsServiceImpl implements UsageStatsService {

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @Resource
    private RagRetrievalLogService ragRetrievalLogService;

    @Resource
    private ToolCallLogService toolCallLogService;

    @Resource
    private com.mio.ai.resource.service.log.SkillUsageLogService skillUsageLogService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private com.mio.ai.resource.service.log.AgentDisplayResolver agentDisplayResolver;

    @Override
    public UsageStatsVO buildStats(String type, LocalDateTime startTime, LocalDateTime endTime, Long userId) {
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        LocalDateTime prevStartTime = startTime.minusMinutes(durationMinutes);
        LocalDateTime prevEndTime = endTime.minusMinutes(durationMinutes);

        switch (type) {
            case "agent":
                return buildAgentStats(startTime, endTime, prevStartTime, prevEndTime, userId);
            case "knowledge":
                return buildKnowledgeStats(startTime, endTime, prevStartTime, prevEndTime, userId);
            case "mcp":
                return buildMcpStats(startTime, endTime, prevStartTime, prevEndTime, userId);
            case "skill":
                return buildSkillStats(startTime, endTime, prevStartTime, prevEndTime, userId);
            default:
                return null;
        }
    }

    private UsageStatsVO buildAgentStats(LocalDateTime startTime, LocalDateTime endTime,
                                         LocalDateTime prevStartTime, LocalDateTime prevEndTime,
                                         Long userId) {
        LambdaQueryWrapper<AgentUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AgentUsageLog::getCreateTime, startTime)
                .le(AgentUsageLog::getCreateTime, endTime);
        if (userId != null) {
            wrapper.eq(AgentUsageLog::getUserId, userId);
        }
        List<AgentUsageLog> logs = agentUsageLogService.list(wrapper);

        LambdaQueryWrapper<AgentUsageLog> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.ge(AgentUsageLog::getCreateTime, prevStartTime)
                .le(AgentUsageLog::getCreateTime, prevEndTime);
        if (userId != null) {
            prevWrapper.eq(AgentUsageLog::getUserId, userId);
        }
        List<AgentUsageLog> prevLogs = agentUsageLogService.list(prevWrapper);

        UsageStatsVO vo = new UsageStatsVO();

        long agentCount = logs.stream().map(AgentUsageLog::getAgentId).filter(Objects::nonNull).distinct().count();
        long successCount = logs.stream().filter(log -> Integer.valueOf(1).equals(log.getStatus())).count();
        long totalTokens = logs.stream()
                .mapToLong(log -> (log.getInputTokens() != null ? log.getInputTokens() : 0)
                        + (log.getOutputTokens() != null ? log.getOutputTokens() : 0))
                .sum();
        long avgTokens = successCount == 0 ? 0 : totalTokens / successCount;

        long prevAgentCount = prevLogs.stream().map(AgentUsageLog::getAgentId).filter(Objects::nonNull).distinct().count();
        long prevSuccessCount = prevLogs.stream().filter(log -> Integer.valueOf(1).equals(log.getStatus())).count();
        long prevTotalTokens = prevLogs.stream()
                .mapToLong(log -> (log.getInputTokens() != null ? log.getInputTokens() : 0)
                        + (log.getOutputTokens() != null ? log.getOutputTokens() : 0))
                .sum();
        long prevAvgTokens = prevSuccessCount == 0 ? 0 : prevTotalTokens / prevSuccessCount;

        Map<String, UsageStatsVO.MetricValue> metrics = new LinkedHashMap<>();
        metrics.put("agentCount", buildMetric(agentCount, "个", calcTrend(agentCount, prevAgentCount)));
        metrics.put("successCount", buildMetric(successCount, "次", calcTrend(successCount, prevSuccessCount)));
        metrics.put("tokenCount", buildMetric(totalTokens, "tokens", calcTrend(totalTokens, prevTotalTokens)));
        metrics.put("avgTokens", buildMetric(avgTokens, "tokens", calcTrend(avgTokens, prevAvgTokens)));
        vo.setMetrics(metrics);

        Map<String, List<UsageStatsVO.TimePoint>> trendMap = buildAgentTrendMap(logs, startTime, endTime);
        Map<String, List<UsageStatsVO.TimePoint>> prevTrendMap = buildAgentTrendMap(prevLogs, prevStartTime, prevEndTime);
        vo.setTrendMap(trendMap);
        vo.setTrend(trendMap.getOrDefault("count", new ArrayList<>()));
        vo.setPrevTrendMap(prevTrendMap);

        Map<Long, Long> agentCallCount = logs.stream()
                .filter(log -> log.getAgentId() != null)
                .collect(Collectors.groupingBy(AgentUsageLog::getAgentId, Collectors.counting()));

        // 真实名称/头像/icon 批量解析；群聊智能体不进图表——群聊统计的是实际对话的成员智能体
        Map<Long, com.mio.ai.resource.service.log.AgentDisplayResolver.Display> agentDisplays =
                agentDisplayResolver.resolve(agentCallCount.keySet());
        List<UsageStatsVO.NameValue> distribution = agentCallCount.entrySet().stream()
                .filter(e -> {
                    var display = agentDisplays.get(e.getKey());
                    return display == null || !display.isGroup();
                })
                .map(e -> {
                    UsageStatsVO.NameValue nv = new UsageStatsVO.NameValue();
                    nv.setId(String.valueOf(e.getKey()));
                    var display = agentDisplays.get(e.getKey());
                    if (display != null) {
                        nv.setName(display.name());
                        nv.setAvatar(display.avatar());
                        nv.setIcon(display.icon());
                    } else {
                        nv.setName("智能体 " + e.getKey());
                    }
                    nv.setValue(e.getValue());
                    return nv;
                })
                .sorted(Comparator.comparingLong((UsageStatsVO.NameValue nv) -> nv.getValue().longValue()).reversed())
                .collect(Collectors.toList());
        vo.setDistribution(distribution);
        vo.setRank(distribution);

        return vo;
    }

    /** 技能使用统计：技能使用日志聚合（模型读取 skills/<id>/ 视为使用） */
    private UsageStatsVO buildSkillStats(LocalDateTime startTime, LocalDateTime endTime,
                                         LocalDateTime prevStartTime, LocalDateTime prevEndTime,
                                         Long userId) {
        LambdaQueryWrapper<com.mio.ai.resource.model.entity.SkillUsageLog> wrapper =
                new LambdaQueryWrapper<>();
        wrapper.ge(com.mio.ai.resource.model.entity.SkillUsageLog::getCreateTime, startTime)
                .le(com.mio.ai.resource.model.entity.SkillUsageLog::getCreateTime, endTime);
        if (userId != null) {
            wrapper.eq(com.mio.ai.resource.model.entity.SkillUsageLog::getUserId, userId);
        }
        List<com.mio.ai.resource.model.entity.SkillUsageLog> logs =
                skillUsageLogService.list(wrapper);

        LambdaQueryWrapper<com.mio.ai.resource.model.entity.SkillUsageLog> prevWrapper =
                new LambdaQueryWrapper<>();
        prevWrapper.ge(com.mio.ai.resource.model.entity.SkillUsageLog::getCreateTime, prevStartTime)
                .le(com.mio.ai.resource.model.entity.SkillUsageLog::getCreateTime, prevEndTime);
        if (userId != null) {
            prevWrapper.eq(com.mio.ai.resource.model.entity.SkillUsageLog::getUserId, userId);
        }
        List<com.mio.ai.resource.model.entity.SkillUsageLog> prevLogs = skillUsageLogService.list(prevWrapper);

        UsageStatsVO vo = new UsageStatsVO();

        long skillCount = logs.stream().map(com.mio.ai.resource.model.entity.SkillUsageLog::getSkillId)
                .filter(Objects::nonNull).distinct().count();
        long useCount = logs.size();
        long agentCount = logs.stream().map(com.mio.ai.resource.model.entity.SkillUsageLog::getAgentId)
                .filter(Objects::nonNull).distinct().count();
        long avgPerAgent = agentCount == 0 ? 0 : useCount / agentCount;

        long prevSkillCount = prevLogs.stream().map(com.mio.ai.resource.model.entity.SkillUsageLog::getSkillId)
                .filter(Objects::nonNull).distinct().count();
        long prevUseCount = prevLogs.size();
        long prevAgentCount = prevLogs.stream().map(com.mio.ai.resource.model.entity.SkillUsageLog::getAgentId)
                .filter(Objects::nonNull).distinct().count();
        long prevAvgPerAgent = prevAgentCount == 0 ? 0 : prevUseCount / prevAgentCount;

        Map<String, UsageStatsVO.MetricValue> metrics = new LinkedHashMap<>();
        metrics.put("agentCount", buildMetric(skillCount, "个", calcTrend(skillCount, prevSkillCount)));
        metrics.put("successCount", buildMetric(useCount, "次", calcTrend(useCount, prevUseCount)));
        metrics.put("tokenCount", buildMetric(agentCount, "个", calcTrend(agentCount, prevAgentCount)));
        metrics.put("avgTokens", buildMetric(avgPerAgent, "次", calcTrend(avgPerAgent, prevAvgPerAgent)));
        vo.setMetrics(metrics);

        Map<String, List<UsageStatsVO.TimePoint>> trendMap = buildSkillTrendMap(logs, startTime, endTime);
        Map<String, List<UsageStatsVO.TimePoint>> prevTrendMap = buildSkillTrendMap(prevLogs, prevStartTime, prevEndTime);
        vo.setTrendMap(trendMap);
        vo.setTrend(trendMap.getOrDefault("count", new ArrayList<>()));
        vo.setPrevTrendMap(prevTrendMap);

        // 分布按 skill_id 聚合：名称优先取日志冗余（技能删除后仍可展示）
        Map<Long, String> skillNames = logs.stream()
                .filter(log -> log.getSkillId() != null)
                .collect(Collectors.toMap(com.mio.ai.resource.model.entity.SkillUsageLog::getSkillId,
                        log -> log.getSkillName() != null ? log.getSkillName() : "技能 " + log.getSkillId(),
                        (first, second) -> first));
        List<UsageStatsVO.NameValue> distribution = logs.stream()
                .filter(log -> log.getSkillId() != null)
                .collect(Collectors.groupingBy(com.mio.ai.resource.model.entity.SkillUsageLog::getSkillId,
                        Collectors.counting()))
                .entrySet().stream()
                .map(e -> {
                    UsageStatsVO.NameValue nv = new UsageStatsVO.NameValue();
                    nv.setId(String.valueOf(e.getKey()));
                    nv.setName(skillNames.getOrDefault(e.getKey(), "技能 " + e.getKey()));
                    nv.setValue(e.getValue());
                    return nv;
                })
                .sorted(Comparator.comparingLong((UsageStatsVO.NameValue nv) -> nv.getValue().longValue()).reversed())
                .collect(Collectors.toList());
        vo.setDistribution(distribution);
        vo.setRank(distribution);

        return vo;
    }

    private Map<String, List<UsageStatsVO.TimePoint>> buildSkillTrendMap(
            List<com.mio.ai.resource.model.entity.SkillUsageLog> logs, LocalDateTime startTime, LocalDateTime endTime) {
        return buildTrendMap(logs, startTime, endTime,
                com.mio.ai.resource.model.entity.SkillUsageLog::getCreateTime,
                bucketLogs -> (long) bucketLogs.size(),
                bucketLogs -> bucketLogs.stream()
                        .map(com.mio.ai.resource.model.entity.SkillUsageLog::getSkillId)
                        .filter(Objects::nonNull).distinct().count(),
                bucketLogs -> bucketLogs.stream()
                        .map(com.mio.ai.resource.model.entity.SkillUsageLog::getAgentId)
                        .filter(Objects::nonNull).distinct().count(),
                bucketLogs -> {
                    long agents = bucketLogs.stream()
                            .map(com.mio.ai.resource.model.entity.SkillUsageLog::getAgentId)
                            .filter(Objects::nonNull).distinct().count();
                    return agents == 0 ? 0L : (long) bucketLogs.size() / agents;
                });
    }

    private UsageStatsVO buildKnowledgeStats(LocalDateTime startTime, LocalDateTime endTime,
                                             LocalDateTime prevStartTime, LocalDateTime prevEndTime,
                                             Long userId) {
        LambdaQueryWrapper<RagRetrievalLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(RagRetrievalLog::getCreateTime, startTime)
                .le(RagRetrievalLog::getCreateTime, endTime);
        if (userId != null) {
            wrapper.eq(RagRetrievalLog::getUserId, userId);
        }
        List<RagRetrievalLog> logs = ragRetrievalLogService.list(wrapper);

        LambdaQueryWrapper<RagRetrievalLog> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.ge(RagRetrievalLog::getCreateTime, prevStartTime)
                .le(RagRetrievalLog::getCreateTime, prevEndTime);
        if (userId != null) {
            prevWrapper.eq(RagRetrievalLog::getUserId, userId);
        }
        List<RagRetrievalLog> prevLogs = ragRetrievalLogService.list(prevWrapper);

        UsageStatsVO vo = new UsageStatsVO();

        long kbCount = logs.stream().map(RagRetrievalLog::getKbId).filter(Objects::nonNull).distinct().count();
        long retrievalCount = logs.size();
        long totalChunks = logs.stream().mapToLong(log -> log.getTopK() != null ? log.getTopK() : 0).sum();
        long avgChunks = retrievalCount == 0 ? 0 : totalChunks / retrievalCount;

        long prevKbCount = prevLogs.stream().map(RagRetrievalLog::getKbId).filter(Objects::nonNull).distinct().count();
        long prevRetrievalCount = prevLogs.size();
        long prevTotalChunks = prevLogs.stream().mapToLong(log -> log.getTopK() != null ? log.getTopK() : 0).sum();
        long prevAvgChunks = prevRetrievalCount == 0 ? 0 : prevTotalChunks / prevRetrievalCount;

        Map<String, UsageStatsVO.MetricValue> metrics = new LinkedHashMap<>();
        metrics.put("agentCount", buildMetric(kbCount, "个", calcTrend(kbCount, prevKbCount)));
        metrics.put("successCount", buildMetric(retrievalCount, "次", calcTrend(retrievalCount, prevRetrievalCount)));
        metrics.put("tokenCount", buildMetric(totalChunks, "chunks", calcTrend(totalChunks, prevTotalChunks)));
        metrics.put("avgTokens", buildMetric(avgChunks, "chunks", calcTrend(avgChunks, prevAvgChunks)));
        vo.setMetrics(metrics);

        Map<String, List<UsageStatsVO.TimePoint>> trendMap = buildKnowledgeTrendMap(logs, startTime, endTime);
        Map<String, List<UsageStatsVO.TimePoint>> prevTrendMap = buildKnowledgeTrendMap(prevLogs, prevStartTime, prevEndTime);
        vo.setTrendMap(trendMap);
        vo.setTrend(trendMap.getOrDefault("count", new ArrayList<>()));
        vo.setPrevTrendMap(prevTrendMap);

        Map<Long, Long> kbRetrievalCount = logs.stream()
                .filter(log -> log.getKbId() != null)
                .collect(Collectors.groupingBy(RagRetrievalLog::getKbId, Collectors.counting()));

        List<UsageStatsVO.NameValue> distribution = kbRetrievalCount.entrySet().stream()
                .map(e -> buildNameValue(e.getKey(), e.getValue(), this::getKnowledgeBaseNameAvatar))
                .sorted(Comparator.comparingLong((UsageStatsVO.NameValue nv) -> nv.getValue().longValue()).reversed())
                .collect(Collectors.toList());
        vo.setDistribution(distribution);
        vo.setRank(distribution);

        return vo;
    }

    private UsageStatsVO buildMcpStats(LocalDateTime startTime, LocalDateTime endTime,
                                       LocalDateTime prevStartTime, LocalDateTime prevEndTime,
                                       Long userId) {
        LambdaQueryWrapper<ToolCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ToolCallLog::getCreateTime, startTime)
                .le(ToolCallLog::getCreateTime, endTime);
        if (userId != null) {
            wrapper.eq(ToolCallLog::getUserId, userId);
        }
        List<ToolCallLog> logs = toolCallLogService.list(wrapper);

        LambdaQueryWrapper<ToolCallLog> prevWrapper = new LambdaQueryWrapper<>();
        prevWrapper.ge(ToolCallLog::getCreateTime, prevStartTime)
                .le(ToolCallLog::getCreateTime, prevEndTime);
        if (userId != null) {
            prevWrapper.eq(ToolCallLog::getUserId, userId);
        }
        List<ToolCallLog> prevLogs = toolCallLogService.list(prevWrapper);

        UsageStatsVO vo = new UsageStatsVO();

        long toolCount = logs.stream().map(ToolCallLog::getName).filter(Objects::nonNull).distinct().count();
        long callCount = logs.size();
        long successCount = logs.stream().filter(log -> Integer.valueOf(1).equals(log.getStatus())).count();
        long avgExecutionTime = callCount == 0 ? 0 : logs.stream()
                .mapToLong(log -> log.getExecutionTime() != null ? log.getExecutionTime() : 0)
                .sum() / callCount;

        long prevToolCount = prevLogs.stream().map(ToolCallLog::getName).filter(Objects::nonNull).distinct().count();
        long prevCallCount = prevLogs.size();
        long prevSuccessCount = prevLogs.stream().filter(log -> Integer.valueOf(1).equals(log.getStatus())).count();
        long prevAvgExecutionTime = prevCallCount == 0 ? 0 : prevLogs.stream()
                .mapToLong(log -> log.getExecutionTime() != null ? log.getExecutionTime() : 0)
                .sum() / prevCallCount;

        Map<String, UsageStatsVO.MetricValue> metrics = new LinkedHashMap<>();
        metrics.put("agentCount", buildMetric(toolCount, "个", calcTrend(toolCount, prevToolCount)));
        metrics.put("successCount", buildMetric(callCount, "次", calcTrend(callCount, prevCallCount)));
        metrics.put("tokenCount", buildMetric(successCount, "次", calcTrend(successCount, prevSuccessCount)));
        metrics.put("avgTokens", buildMetric(avgExecutionTime, "ms", calcTrend(avgExecutionTime, prevAvgExecutionTime)));
        vo.setMetrics(metrics);

        Map<String, List<UsageStatsVO.TimePoint>> trendMap = buildMcpTrendMap(logs, startTime, endTime);
        Map<String, List<UsageStatsVO.TimePoint>> prevTrendMap = buildMcpTrendMap(prevLogs, prevStartTime, prevEndTime);
        vo.setTrendMap(trendMap);
        vo.setTrend(trendMap.getOrDefault("count", new ArrayList<>()));
        vo.setPrevTrendMap(prevTrendMap);

        Map<String, Long> toolCallCount = logs.stream()
                .filter(log -> log.getName() != null && !log.getName().isEmpty())
                .collect(Collectors.groupingBy(ToolCallLog::getName, Collectors.counting()));

        List<UsageStatsVO.NameValue> distribution = toolCallCount.entrySet().stream()
                .map(e -> {
                    UsageStatsVO.NameValue nv = new UsageStatsVO.NameValue();
                    nv.setName(e.getKey());
                    nv.setValue(e.getValue());
                    return nv;
                })
                .sorted(Comparator.comparingLong((UsageStatsVO.NameValue nv) -> nv.getValue().longValue()).reversed())
                .collect(Collectors.toList());
        vo.setDistribution(distribution);
        vo.setRank(distribution);

        return vo;
    }

    private UsageStatsVO.MetricValue buildMetric(Number value, String unit, Number trend) {
        UsageStatsVO.MetricValue mv = new UsageStatsVO.MetricValue();
        mv.setValue(value);
        mv.setUnit(unit);
        mv.setTrend(trend);
        return mv;
    }

    private double calcTrend(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return (double) (current - previous) * 100 / previous;
    }

    private UsageStatsVO.NameValue buildNameValue(Long id, Long value,
                                                   Function<Long, UsageStatsVO.NameValue> resolver) {
        UsageStatsVO.NameValue nv = resolver.apply(id);
        nv.setValue(value);
        return nv;
    }

    private UsageStatsVO.NameValue getKnowledgeBaseNameAvatar(Long kbId) {
        UsageStatsVO.NameValue nv = new UsageStatsVO.NameValue();
        nv.setId(String.valueOf(kbId));
        if (Long.valueOf(0L).equals(kbId)) {
            nv.setName("系统内置知识库");
            nv.setAvatar(null);
            return nv;
        }
        try {
            com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO kb = knowledgeBaseService.getKnowledgeBaseById(kbId);
            if (kb != null) {
                nv.setName(kb.getName());
                nv.setAvatar(null);
                return nv;
            }
        } catch (Exception ignored) {
        }
        nv.setName("知识库 " + kbId);
        nv.setAvatar(null);
        return nv;
    }

    private Map<String, List<UsageStatsVO.TimePoint>> buildAgentTrendMap(
            List<AgentUsageLog> logs, LocalDateTime startTime, LocalDateTime endTime) {
        return buildTrendMap(logs, startTime, endTime, AgentUsageLog::getCreateTime,
                bucketLogs -> (long) bucketLogs.size(),
                bucketLogs -> bucketLogs.stream().map(AgentUsageLog::getAgentId).filter(Objects::nonNull).distinct().count(),
                bucketLogs -> bucketLogs.stream()
                        .mapToLong(log -> (log.getInputTokens() != null ? log.getInputTokens() : 0)
                                + (log.getOutputTokens() != null ? log.getOutputTokens() : 0))
                        .sum(),
                bucketLogs -> {
                    long count = bucketLogs.size();
                    if (count == 0) return 0L;
                    long total = bucketLogs.stream()
                            .mapToLong(log -> (log.getInputTokens() != null ? log.getInputTokens() : 0)
                                    + (log.getOutputTokens() != null ? log.getOutputTokens() : 0))
                            .sum();
                    return total / count;
                });
    }

    private Map<String, List<UsageStatsVO.TimePoint>> buildKnowledgeTrendMap(
            List<RagRetrievalLog> logs, LocalDateTime startTime, LocalDateTime endTime) {
        return buildTrendMap(logs, startTime, endTime, RagRetrievalLog::getCreateTime,
                bucketLogs -> (long) bucketLogs.size(),
                bucketLogs -> bucketLogs.stream().map(RagRetrievalLog::getKbId).filter(Objects::nonNull).distinct().count(),
                bucketLogs -> bucketLogs.stream().mapToLong(log -> log.getTopK() != null ? log.getTopK() : 0).sum(),
                bucketLogs -> {
                    long count = bucketLogs.size();
                    return count == 0 ? 0L : bucketLogs.stream().mapToLong(log -> log.getTopK() != null ? log.getTopK() : 0).sum() / count;
                });
    }

    private Map<String, List<UsageStatsVO.TimePoint>> buildMcpTrendMap(
            List<ToolCallLog> logs, LocalDateTime startTime, LocalDateTime endTime) {
        return buildTrendMap(logs, startTime, endTime, ToolCallLog::getCreateTime,
                bucketLogs -> (long) bucketLogs.size(),
                bucketLogs -> bucketLogs.stream().map(ToolCallLog::getName).filter(Objects::nonNull).distinct().count(),
                bucketLogs -> bucketLogs.stream().filter(log -> Integer.valueOf(1).equals(log.getStatus())).count(),
                bucketLogs -> {
                    long count = bucketLogs.size();
                    return count == 0 ? 0L : bucketLogs.stream()
                            .mapToLong(log -> log.getExecutionTime() != null ? log.getExecutionTime() : 0)
                            .sum() / count;
                });
    }

    private <T> Map<String, List<UsageStatsVO.TimePoint>> buildTrendMap(
            List<T> logs,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Function<T, java.util.Date> timeExtractor,
            Function<List<T>, Long> countExtractor,
            Function<List<T>, Long> distinctCountExtractor,
            Function<List<T>, Long> totalExtractor,
            Function<List<T>, Long> avgExtractor) {

        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        String pattern;
        long stepMinutes;
        if (minutes <= 60) {
            pattern = "HH:mm:ss";
            stepMinutes = 1;
        } else if (minutes <= 6 * 60) {
            pattern = "HH:mm:ss";
            stepMinutes = 10;
        } else if (minutes <= 24 * 60) {
            pattern = "HH:mm:ss";
            stepMinutes = 30;
        } else if (minutes <= 7 * 24 * 60) {
            pattern = "MM-dd HH:mm";
            stepMinutes = 60;
        } else {
            pattern = "MM-dd";
            stepMinutes = 24 * 60;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        DateTimeFormatter fullTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Function<java.util.Date, LocalDateTime> toLocalDateTime = date ->
                date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        Map<LocalDateTime, List<T>> bucketMap = logs.stream()
                .collect(Collectors.groupingBy(log -> toBucket(toLocalDateTime.apply(timeExtractor.apply(log)), stepMinutes)));

        Map<String, List<UsageStatsVO.TimePoint>> result = new LinkedHashMap<>();
        List<UsageStatsVO.TimePoint> countList = new ArrayList<>();
        List<UsageStatsVO.TimePoint> agentCountList = new ArrayList<>();
        List<UsageStatsVO.TimePoint> tokensList = new ArrayList<>();
        List<UsageStatsVO.TimePoint> avgTokensList = new ArrayList<>();

        LocalDateTime current = toBucket(startTime, stepMinutes);
        while (!current.isAfter(endTime)) {
            String timeLabel = current.format(formatter);
            String fullTimeLabel = current.format(fullTimeFormatter);
            List<T> bucketLogs = bucketMap.getOrDefault(current, new ArrayList<>());

            UsageStatsVO.TimePoint countPoint = new UsageStatsVO.TimePoint();
            countPoint.setTime(timeLabel);
            countPoint.setFullTime(fullTimeLabel);
            countPoint.setValue(countExtractor.apply(bucketLogs));
            countList.add(countPoint);

            UsageStatsVO.TimePoint agentCountPoint = new UsageStatsVO.TimePoint();
            agentCountPoint.setTime(timeLabel);
            agentCountPoint.setFullTime(fullTimeLabel);
            agentCountPoint.setValue(distinctCountExtractor.apply(bucketLogs));
            agentCountList.add(agentCountPoint);

            UsageStatsVO.TimePoint tokensPoint = new UsageStatsVO.TimePoint();
            tokensPoint.setTime(timeLabel);
            tokensPoint.setFullTime(fullTimeLabel);
            tokensPoint.setValue(totalExtractor.apply(bucketLogs));
            tokensList.add(tokensPoint);

            UsageStatsVO.TimePoint avgTokensPoint = new UsageStatsVO.TimePoint();
            avgTokensPoint.setTime(timeLabel);
            avgTokensPoint.setFullTime(fullTimeLabel);
            avgTokensPoint.setValue(avgExtractor.apply(bucketLogs));
            avgTokensList.add(avgTokensPoint);

            current = current.plusMinutes(stepMinutes);
        }

        result.put("count", countList);
        result.put("agentCount", agentCountList);
        result.put("tokens", tokensList);
        result.put("avgTokens", avgTokensList);
        return result;
    }

    private LocalDateTime toBucket(LocalDateTime time, long stepMinutes) {
        time = time.truncatedTo(ChronoUnit.SECONDS);
        if (stepMinutes >= 24 * 60) {
            return time.truncatedTo(ChronoUnit.DAYS);
        } else if (stepMinutes >= 60) {
            return time.truncatedTo(ChronoUnit.HOURS);
        } else {
            int minute = time.getMinute();
            int bucketMinute = (minute / (int) stepMinutes) * (int) stepMinutes;
            return time.withMinute(bucketMinute).withSecond(0).withNano(0);
        }
    }
}
