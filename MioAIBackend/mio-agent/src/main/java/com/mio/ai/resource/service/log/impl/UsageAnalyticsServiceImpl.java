package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.resource.model.entity.UserStatsDaily;
import com.mio.ai.resource.model.vo.log.UsageAnalyticsVO;
import com.mio.ai.resource.model.vo.log.UsageDayDetailVO;
import com.mio.ai.resource.model.vo.log.UsageStatsVO;
import com.mio.ai.resource.service.log.UserStatsDailyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 使用记录深度分析服务实现：使用去向、使用构成、使用明细、日历单日明细。
 * 数据统一来自 UsageAnalyticsRowLoader，保证各处口径一致。
 */
@Component
public class UsageAnalyticsServiceImpl {

    /** 散点上限：超出按时间等距抽样，避免一次返回上万点拖垮前端 */
    private static final int SCATTER_LIMIT = 1200;

    @Resource
    private UsageAnalyticsRowLoader rowLoader;

    @Resource
    private UserStatsDailyService userStatsDailyService;

    public UsageAnalyticsVO buildAnalytics(String type, LocalDateTime startTime, LocalDateTime endTime,
                                          Long userId, Long agentId, boolean userLayer) {
        UsageAnalyticsVO vo = new UsageAnalyticsVO();
        vo.setType(type);
        applyLabels(vo, type);

        UsageAnalyticsRowLoader.LoadedRows loaded =
                rowLoader.load(type, startTime, endTime, userId, agentId, userLayer);
        List<UsageRow> rows = loaded.rows();

        vo.setFlow(UsageFlowBuilder.build(type, rows, loaded.userNames(), userLayer));
        vo.setDrill(UsageDrillBuilder.build(type, rows));
        vo.setTotalEvents(rows.size());
        vo.setEvents(sampleEvents(rows));
        return vo;
    }

    /** 日历热力图点击某天：当日汇总 + 时段分布 + 智能体/模型排行 + 其它资源计数 */
    public UsageDayDetailVO buildDayDetail(Long userId, String type, String dateText) {
        LocalDate date = LocalDate.parse(dateText);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        UsageDayDetailVO vo = new UsageDayDetailVO();
        vo.setDate(dateText);
        vo.setAgents(new ArrayList<>());
        vo.setModels(new ArrayList<>());
        vo.setHours(new ArrayList<>());

        fillDailySummary(vo, userId, dayStart, dayEnd);
        fillRows(vo, type, userId, dayStart, dayEnd);
        fillResourceCounts(vo, type, userId, dayStart, dayEnd);
        return vo;
    }

    private void fillDailySummary(UsageDayDetailVO vo, Long userId, LocalDateTime start, LocalDateTime end) {
        List<UserStatsDaily> dailyRows = userStatsDailyService.list(
                new LambdaQueryWrapper<UserStatsDaily>()
                        .ge(UserStatsDaily::getStatDate, toDate(start))
                        .lt(UserStatsDaily::getStatDate, toDate(end))
                        .eq(userId != null, UserStatsDaily::getUserId, userId));
        for (UserStatsDaily row : dailyRows) {
            long input = row.getInputTokens() != null ? row.getInputTokens() : 0;
            long output = row.getOutputTokens() != null ? row.getOutputTokens() : 0;
            vo.setInputTokens(vo.getInputTokens() + input);
            vo.setOutputTokens(vo.getOutputTokens() + output);
            vo.setTokens(vo.getTokens() + input + output);
            vo.setCalls(vo.getCalls() + (row.getCallCount() != null ? row.getCallCount() : 0));
            vo.setChatDurationMs(vo.getChatDurationMs()
                    + (row.getChatDurationMs() != null ? row.getChatDurationMs() : 0));
        }
    }

    private void fillRows(UsageDayDetailVO vo, String type, Long userId,
                          LocalDateTime start, LocalDateTime end) {
        List<UsageRow> rows = rowLoader.load(type, start, end, userId, null, false).rows();
        long[] hourMeasure = new long[24];
        long[] hourCalls = new long[24];
        Map<String, long[]> agentAgg = new LinkedHashMap<>();
        Map<String, long[]> modelAgg = new LinkedHashMap<>();
        Set<String> conversations = new HashSet<>();

        for (UsageRow row : rows) {
            int hour = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.time()), ZoneId.systemDefault()).getHour();
            hourMeasure[hour] += (long) row.measure();
            hourCalls[hour] += 1;
            if (row.conversationId() != null) {
                conversations.add(row.conversationId());
            }
            agentAgg.computeIfAbsent(row.agentName(), ignored -> new long[2]);
            agentAgg.get(row.agentName())[0] += row.weight();
            agentAgg.get(row.agentName())[1] += 1;
            if (row.model() != null && !row.model().isBlank()) {
                modelAgg.computeIfAbsent(row.model(), ignored -> new long[2]);
                modelAgg.get(row.model())[0] += row.weight();
                modelAgg.get(row.model())[1] += 1;
            }
        }

        for (int hour = 0; hour < 24; hour++) {
            UsageDayDetailVO.HourBucket bucket = new UsageDayDetailVO.HourBucket();
            bucket.setHour(hour);
            bucket.setMeasure(hourMeasure[hour]);
            bucket.setCalls(hourCalls[hour]);
            vo.getHours().add(bucket);
        }
        vo.setAgents(toNameValues(agentAgg));
        vo.setModels(toNameValues(modelAgg));
        vo.setConversations(conversations.size());
        if (vo.getCalls() == 0) {
            // user_stats_daily 尚未覆盖的历史日期：用当前类型日志量兜底
            vo.setCalls(rows.size());
        }
        // agent 口径的“资源”是智能体本身；其余类型是目标（知识库/工具/技能）
        long activeCount = "agent".equals(type)
                ? rows.stream().map(UsageRow::agentName).distinct().count()
                : rows.stream().map(UsageRow::targetName).distinct().count();
        setResourceCount(vo, type, rows.size(), activeCount);
    }

    /** 三类资源的当日调用数：日明细里与主类型并列展示，避免切 tab 才能看到 */
    private void fillResourceCounts(UsageDayDetailVO vo, String type, Long userId,
                                    LocalDateTime start, LocalDateTime end) {
        if (!"agent".equals(type)) {
            fillResourceCount(vo, userId, start, end, "agent");
        }
        if (!"knowledge".equals(type)) {
            fillResourceCount(vo, userId, start, end, "knowledge");
        }
        if (!"mcp".equals(type)) {
            fillResourceCount(vo, userId, start, end, "mcp");
        }
        if (!"skill".equals(type)) {
            fillResourceCount(vo, userId, start, end, "skill");
        }
    }

    private void fillResourceCount(UsageDayDetailVO vo, Long userId,
                                   LocalDateTime start, LocalDateTime end, String type) {
        List<UsageRow> rows = rowLoader.load(type, start, end, userId, null, false).rows();
        long count = "agent".equals(type)
                ? rows.stream().map(UsageRow::agentName).distinct().count()
                : rows.stream().map(UsageRow::targetName).distinct().count();
        setResourceCount(vo, type, rows.size(), count);
    }

    private void setResourceCount(UsageDayDetailVO vo, String type, long calls, long count) {
        switch (type) {
            case "agent" -> {
                vo.setAgentCalls(calls);
                vo.setAgentCount(count);
            }
            case "knowledge" -> {
                vo.setKnowledgeCalls(calls);
                vo.setKnowledgeCount(count);
            }
            case "mcp" -> {
                vo.setMcpCalls(calls);
                vo.setMcpCount(count);
            }
            case "skill" -> {
                vo.setSkillCalls(calls);
                vo.setSkillCount(count);
            }
            default -> {
            }
        }
    }

    private List<UsageStatsVO.NameValue> toNameValues(Map<String, long[]> agg) {
        return agg.entrySet().stream()
                .map(entry -> {
                    UsageStatsVO.NameValue nv = new UsageStatsVO.NameValue();
                    nv.setId(entry.getKey());
                    nv.setName(entry.getKey());
                    nv.setValue(entry.getValue()[0]);
                    nv.setCalls(entry.getValue()[1]);
                    return nv;
                })
                .sorted(Comparator.comparingLong((UsageStatsVO.NameValue nv) -> nv.getValue().longValue()).reversed())
                .limit(8)
                .collect(Collectors.toList());
    }

    /** 按时间等距抽样，保留首尾与整体分布形态 */
    private List<UsageAnalyticsVO.EventPoint> sampleEvents(List<UsageRow> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<UsageRow> sorted = rows.stream()
                .sorted(Comparator.comparingLong(UsageRow::time))
                .toList();
        List<UsageAnalyticsVO.EventPoint> points = new ArrayList<>();
        int total = sorted.size();
        int step = Math.max(1, (int) Math.ceil((double) total / SCATTER_LIMIT));
        for (int i = 0; i < total; i += step) {
            points.add(toPoint(sorted.get(i)));
        }
        if (!points.isEmpty() && (total - 1) % step != 0) {
            UsageAnalyticsVO.EventPoint last = toPoint(sorted.get(total - 1));
            if (points.get(points.size() - 1).getTime() != last.getTime()) {
                points.add(last);
            }
        }
        return points;
    }

    private UsageAnalyticsVO.EventPoint toPoint(UsageRow row) {
        UsageAnalyticsVO.EventPoint point = new UsageAnalyticsVO.EventPoint();
        point.setTime(row.time());
        point.setValue(row.measure());
        point.setStatus(row.status());
        point.setAgentName(row.agentName());
        point.setModel(row.model());
        point.setTargetName(row.targetName());
        return point;
    }

    private void applyLabels(UsageAnalyticsVO vo, String type) {
        switch (type) {
            case "knowledge" -> {
                vo.setValueLabel("检索次数");
                vo.setValueUnit("次");
                vo.setMeasureLabel("召回 Chunk 数");
                vo.setMeasureUnit("chunks");
            }
            case "mcp" -> {
                vo.setValueLabel("调用次数");
                vo.setValueUnit("次");
                vo.setMeasureLabel("执行耗时");
                vo.setMeasureUnit("ms");
            }
            case "skill" -> {
                vo.setValueLabel("使用次数");
                vo.setValueUnit("次");
                vo.setMeasureLabel("使用次数");
                vo.setMeasureUnit("次");
            }
            default -> {
                vo.setValueLabel("Token 数");
                vo.setValueUnit("tokens");
                vo.setMeasureLabel("Token 数");
                vo.setMeasureUnit("tokens");
            }
        }
    }

    private static Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
