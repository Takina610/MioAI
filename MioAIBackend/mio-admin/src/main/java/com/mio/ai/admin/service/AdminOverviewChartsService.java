package com.mio.ai.admin.service;

import com.mio.ai.admin.model.vo.AdminOverviewChartsVO;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.UserStatsDaily;
import com.mio.ai.resource.model.vo.log.TokenTrendVO;
import com.mio.ai.resource.model.vo.log.UsageStatsVO;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.UserStatsDailyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.admin.component.AdminNameResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台总览图表聚合：全站口径的每日趋势 / 模型 Token 趋势 / 智能体调用分布 / 用户 Token 排行。
 * 聚合口径对齐用户侧洞察（UserInsightServiceImpl）：趋势序列上限 6、群聊本体不进分布图、
 * 模型维度只认已记录模型名的日志。
 */
@Slf4j
@Service
public class AdminOverviewChartsService {

    private static final int TREND_SERIES_LIMIT = 6;

    private static final int DISTRIBUTION_LIMIT = 8;

    private static final int USER_RANK_LIMIT = 10;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @Resource
    private UserStatsDailyService userStatsDailyService;

    @Resource
    private AdminNameResolver nameResolver;

    public AdminOverviewChartsVO buildCharts(Integer days) {
        int rangeDays = days == null ? 30 : Math.max(1, Math.min(90, days));
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(rangeDays - 1L);
        List<LocalDate> allDates = start.datesUntil(today.plusDays(1)).toList();

        Date startDate = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<AgentUsageLog> logs = agentUsageLogService.list(new LambdaQueryWrapper<AgentUsageLog>()
                .ge(AgentUsageLog::getCreateTime, startDate));

        AdminOverviewChartsVO vo = new AdminOverviewChartsVO();
        vo.setDays(rangeDays);
        vo.setTrend(buildDailyTrend(logs, allDates));
        vo.setModelTrend(buildModelTrend(logs, allDates, start));
        vo.setAgentDistribution(buildAgentDistribution(logs));
        vo.setUserRank(buildUserRank(start));
        return vo;
    }

    /** 每日趋势：轮次 / Token / 失败轮次，空日补零 */
    private List<AdminOverviewChartsVO.DayTrend> buildDailyTrend(List<AgentUsageLog> logs, List<LocalDate> allDates) {
        Map<LocalDate, long[]> byDate = new HashMap<>();
        for (AgentUsageLog row : logs) {
            LocalDate date = toLocalDate(row.getCreateTime());
            if (date == null) {
                continue;
            }
            long[] bucket = byDate.computeIfAbsent(date, k -> new long[3]);
            bucket[0] += 1;
            bucket[1] += tokenSum(row);
            if (row.getStatus() != null && row.getStatus() == 0) {
                bucket[2] += 1;
            }
        }
        List<AdminOverviewChartsVO.DayTrend> trend = new ArrayList<>();
        for (LocalDate date : allDates) {
            AdminOverviewChartsVO.DayTrend point = new AdminOverviewChartsVO.DayTrend();
            point.setDate(DAY_FMT.format(date));
            long[] bucket = byDate.get(date);
            if (bucket != null) {
                point.setTurns(bucket[0]);
                point.setTokens(bucket[1]);
                point.setFailedTurns(bucket[2]);
            }
            trend.add(point);
        }
        return trend;
    }

    /** 模型 Token 日趋势：按总量排序取 Top 6，其余并入"其他" */
    private List<TokenTrendVO.Series> buildModelTrend(List<AgentUsageLog> logs, List<LocalDate> allDates, LocalDate start) {
        Map<String, Map<LocalDate, long[]>> grouped = new LinkedHashMap<>();
        for (AgentUsageLog row : logs) {
            if (row.getModel() == null || row.getModel().isBlank()) {
                continue;
            }
            LocalDate date = toLocalDate(row.getCreateTime());
            if (date == null || date.isBefore(start)) {
                continue;
            }
            grouped.computeIfAbsent(row.getModel(), k -> new HashMap<>())
                    .computeIfAbsent(date, d -> new long[2]);
            long[] bucket = grouped.get(row.getModel()).get(date);
            bucket[0] += tokenSum(row);
            bucket[1] += 1;
        }
        List<Map.Entry<String, Map<LocalDate, long[]>>> ranked = grouped.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Map<LocalDate, long[]>> e) ->
                        e.getValue().values().stream().mapToLong(b -> b[0]).sum()).reversed())
                .toList();

        List<TokenTrendVO.Series> series = new ArrayList<>();
        ranked.stream().limit(TREND_SERIES_LIMIT)
                .forEach(entry -> series.add(buildSeries(entry.getKey(), entry.getValue(), allDates)));
        long restTokens = ranked.stream().skip(TREND_SERIES_LIMIT)
                .mapToLong(e -> e.getValue().values().stream().mapToLong(b -> b[0]).sum()).sum();
        long restCalls = ranked.stream().skip(TREND_SERIES_LIMIT)
                .mapToLong(e -> e.getValue().values().stream().mapToLong(b -> b[1]).sum()).sum();
        if (restTokens > 0) {
            Map<LocalDate, long[]> restByDate = new HashMap<>();
            for (Map<LocalDate, long[]> byDate : ranked.stream().skip(TREND_SERIES_LIMIT).map(Map.Entry::getValue).toList()) {
                byDate.forEach((date, bucket) -> {
                    long[] rest = restByDate.computeIfAbsent(date, d -> new long[2]);
                    rest[0] += bucket[0];
                    rest[1] += bucket[1];
                });
            }
            series.add(buildSeries("其他", restByDate, allDates));
        }
        return series;
    }

    private TokenTrendVO.Series buildSeries(String name, Map<LocalDate, long[]> byDate, List<LocalDate> allDates) {
        TokenTrendVO.Series series = new TokenTrendVO.Series();
        series.setId(name);
        series.setName(name);
        long totalTokens = 0;
        long totalCalls = 0;
        List<TokenTrendVO.Point> points = new ArrayList<>();
        for (LocalDate date : allDates) {
            TokenTrendVO.Point point = new TokenTrendVO.Point();
            point.setDate(DAY_FMT.format(date));
            long[] bucket = byDate.get(date);
            if (bucket != null) {
                point.setTokens(bucket[0]);
                totalTokens += bucket[0];
                totalCalls += bucket[1];
            }
            points.add(point);
        }
        series.setTotalTokens(totalTokens);
        series.setTotalCalls(totalCalls);
        series.setPoints(points);
        return series;
    }

    /** 智能体调用次数分布：群聊本体不进图（口径对齐用户侧），取 Top 8 */
    private List<UsageStatsVO.NameValue> buildAgentDistribution(List<AgentUsageLog> logs) {
        Map<Long, Long> callCount = new LinkedHashMap<>();
        for (AgentUsageLog row : logs) {
            if (row.getAgentId() != null) {
                callCount.merge(row.getAgentId(), 1L, Long::sum);
            }
        }
        Map<Long, AgentDisplayResolver.Display> displays = nameResolver.resolveAgents(callCount.keySet());
        return callCount.entrySet().stream()
                .filter(e -> {
                    AgentDisplayResolver.Display display = displays.get(e.getKey());
                    return display == null || !display.isGroup();
                })
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(DISTRIBUTION_LIMIT)
                .map(e -> {
                    AgentDisplayResolver.Display display = displays.get(e.getKey());
                    UsageStatsVO.NameValue item = new UsageStatsVO.NameValue();
                    item.setId(String.valueOf(e.getKey()));
                    item.setName(display != null ? display.name() : "智能体 " + e.getKey());
                    item.setAvatar(display != null ? display.avatar() : null);
                    item.setIcon(display != null ? display.icon() : null);
                    item.setValue(e.getValue());
                    return item;
                })
                .toList();
    }

    /** 用户 Token 排行：user_stats_daily 按日期窗口聚合，取 Top 10 */
    private List<UsageStatsVO.NameValue> buildUserRank(LocalDate start) {
        List<UserStatsDaily> rows = userStatsDailyService.list(new LambdaQueryWrapper<UserStatsDaily>()
                .ge(UserStatsDaily::getStatDate, Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant())));
        Map<Long, Long> tokensByUser = new LinkedHashMap<>();
        for (UserStatsDaily row : rows) {
            if (row.getUserId() != null) {
                tokensByUser.merge(row.getUserId(), tokenSum(row), Long::sum);
            }
        }
        Map<Long, AdminNameResolver.UserDisplay> users = nameResolver.resolveUsers(tokensByUser.keySet());
        return tokensByUser.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(USER_RANK_LIMIT)
                .map(e -> {
                    AdminNameResolver.UserDisplay user = users.get(e.getKey());
                    UsageStatsVO.NameValue item = new UsageStatsVO.NameValue();
                    item.setId(String.valueOf(e.getKey()));
                    item.setName(user != null ? user.name() : "用户 " + e.getKey());
                    item.setAvatar(user != null ? user.avatar() : null);
                    item.setValue(e.getValue());
                    return item;
                })
                .toList();
    }

    private long tokenSum(AgentUsageLog row) {
        return (row.getInputTokens() != null ? row.getInputTokens() : 0)
                + (row.getOutputTokens() != null ? row.getOutputTokens() : 0);
    }

    private long tokenSum(UserStatsDaily row) {
        return (row.getInputTokens() != null ? row.getInputTokens() : 0)
                + (row.getOutputTokens() != null ? row.getOutputTokens() : 0);
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
