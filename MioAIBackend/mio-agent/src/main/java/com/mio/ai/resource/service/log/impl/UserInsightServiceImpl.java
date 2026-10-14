package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.bot.mapper.AgentMessageMapper;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.resource.mapper.log.UserStatsDailyMapper;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.entity.SkillUsageLog;
import com.mio.ai.resource.model.entity.ToolCallLog;
import com.mio.ai.resource.model.entity.UserStatsDaily;
import com.mio.ai.resource.model.entity.RagRetrievalLog;
import com.mio.ai.resource.model.vo.log.ContributionVO;
import com.mio.ai.resource.model.vo.log.TokenTrendVO;
import com.mio.ai.resource.model.vo.log.UserProfileVO;
import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.log.AgentDisplayResolver;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.resource.service.log.RagRetrievalLogService;
import com.mio.ai.resource.service.log.SkillUsageLogService;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.resource.service.log.UserActivityLogService;
import com.mio.ai.resource.service.log.UserInsightService;
import com.mio.ai.resource.service.log.UserStatsDailyService;
import com.mio.ai.resource.service.mcp.McpToolService;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.user.model.entity.User;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 用户数据洞察服务实现类：画像/贡献图/Token 趋势的全部聚合。
 * 每日粒度来自 user_stats_daily（轻），模型/智能体拆分来自 agent_usage_log（有 user_id 索引）。
 */
@Slf4j
@Service
public class UserInsightServiceImpl implements UserInsightService {

    /** 趋势序列上限：超出按总量并入"其他"（折线/饼图序列过多不可读） */
    private static final int TREND_SERIES_LIMIT = 6;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private UserStatsDailyService userStatsDailyService;

    @Resource
    private UserStatsDailyMapper userStatsDailyMapper;

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @Resource
    private ToolCallLogService toolCallLogService;

    @Resource
    private RagRetrievalLogService ragRetrievalLogService;

    @Resource
    private SkillUsageLogService skillUsageLogService;

    @Resource
    private UserActivityLogService userActivityLogService;

    @Resource
    private AgentService agentService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private McpToolService mcpToolService;

    @Resource
    private SkillService skillService;

    @Resource
    private UserService userService;

    @Resource
    private ChatConversationDOMapper chatConversationDOMapper;

    @Resource
    private AgentMessageMapper agentMessageMapper;

    @Resource
    private AgentDisplayResolver agentDisplayResolver;

    @Override
    public UserProfileVO buildProfile(Long userId) {
        UserProfileVO vo = new UserProfileVO();
        if (userId == null) {
            return zeroProfile(vo);
        }

        User user = userService.getById(userId);
        if (user != null && user.getCreateTime() != null) {
            vo.setRegisterTime(user.getCreateTime());
            vo.setRegisterDays(Math.max(1, ChronoUnit.DAYS.between(
                    user.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                    LocalDate.now()) + 1));
        }

        List<UserStatsDaily> dailyRows = userStatsDailyService.listByUser(userId);
        long totalTokens = 0;
        long totalTurns = 0;
        for (UserStatsDaily row : dailyRows) {
            totalTokens += tokenSum(row);
            totalTurns += row.getCallCount() != null ? row.getCallCount() : 0;
        }
        vo.setActiveDays((long) dailyRows.size());
        vo.setTotalTokens(totalTokens);
        vo.setTotalTurns(totalTurns);
        vo.setAvgDailyTokens(dailyRows.isEmpty() ? 0 : totalTokens / dailyRows.size());
        vo.setTotalConversations(chatConversationDOMapper.selectCount(
                new LambdaQueryWrapper<com.mio.ai.bot.model.entity.ChatConversationDO>()
                        .eq(com.mio.ai.bot.model.entity.ChatConversationDO::getUserId, userId)));
        vo.setTotalMessages(agentMessageMapper.selectCount(
                new LambdaQueryWrapper<com.mio.ai.bot.model.entity.AgentMessageDO>()
                        .eq(com.mio.ai.bot.model.entity.AgentMessageDO::getUserId, userId)));

        // 最常用模型/智能体（全历史用量日志聚合；模型维度只认已记录模型名的日志）
        List<AgentUsageLog> logs = listUserUsageLogs(userId);
        Map<String, long[]> byModel = new HashMap<>();
        Map<Long, Long> byAgent = new HashMap<>();
        for (AgentUsageLog row : logs) {
            String model = row.getModel() == null || row.getModel().isBlank() ? null : row.getModel();
            if (model != null) {
                long[] buckets = byModel.computeIfAbsent(model, k -> new long[2]);
                buckets[0] += tokenSum(row);
                buckets[1] += 1;
            }
            if (row.getAgentId() != null) {
                byAgent.merge(row.getAgentId(), tokenSum(row), Long::sum);
            }
        }
        byModel.entrySet().stream()
                .max(Comparator.comparingLong(e -> e.getValue()[0]))
                .ifPresent(e -> {
                    vo.setFavoriteModel(e.getKey());
                    vo.setFavoriteModelTokens(e.getValue()[0]);
                });
        if (!byAgent.isEmpty()) {
            Map<Long, AgentDisplayResolver.Display> displays =
                    agentDisplayResolver.resolve(byAgent.keySet());
            Long topAgentId = byAgent.entrySet().stream()
                    .filter(e -> {
                        AgentDisplayResolver.Display display = displays.get(e.getKey());
                        return display == null || !display.isGroup();
                    })
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey).orElse(null);
            if (topAgentId != null) {
                vo.setFavoriteAgentId(topAgentId);
                AgentDisplayResolver.Display display = displays.get(topAgentId);
                if (display != null) {
                    vo.setFavoriteAgentName(display.name());
                    vo.setFavoriteAgentAvatar(display.avatar());
                }
                vo.setFavoriteAgentTokens(byAgent.get(topAgentId));
            }
        }

        vo.setAgentCount(agentService.count(new LambdaQueryWrapper<com.mio.ai.resource.model.entity.Agent>()
                .eq(com.mio.ai.resource.model.entity.Agent::getUserId, userId)));
        vo.setKnowledgeCount(knowledgeBaseService.count(
                new LambdaQueryWrapper<com.mio.ai.resource.model.entity.KnowledgeBase>()
                        .eq(com.mio.ai.resource.model.entity.KnowledgeBase::getUserId, userId)));
        vo.setMcpCount(mcpToolService.count(new LambdaQueryWrapper<com.mio.ai.resource.model.entity.McpTool>()
                .eq(com.mio.ai.resource.model.entity.McpTool::getUserId, userId)));
        vo.setSkillCount(skillService.count(new LambdaQueryWrapper<com.mio.ai.resource.model.entity.Skill>()
                .eq(com.mio.ai.resource.model.entity.Skill::getUserId, userId)));
        vo.setToolCallCount(toolCallLogService.count(
                new LambdaQueryWrapper<ToolCallLog>().eq(ToolCallLog::getUserId, userId)));
        vo.setRetrievalCount(ragRetrievalLogService.count(
                new LambdaQueryWrapper<RagRetrievalLog>().eq(RagRetrievalLog::getUserId, userId)));
        vo.setSkillUseCount(skillUsageLogService.count(
                new LambdaQueryWrapper<SkillUsageLog>().eq(SkillUsageLog::getUserId, userId)));
        vo.setActivityCount(userActivityLogService.count(
                new LambdaQueryWrapper<com.mio.ai.resource.model.entity.UserActivityLog>()
                        .eq(com.mio.ai.resource.model.entity.UserActivityLog::getUserId, userId)));
        return vo;
    }

    @Override
    public ContributionVO buildContribution(Long userId, Integer year) {
        ContributionVO vo = new ContributionVO();
        vo.setDays(new ArrayList<>());
        ContributionVO.Summary summary = new ContributionVO.Summary();
        summary.setTotalTokens(0L);
        summary.setPeakTokens(0L);
        summary.setLongestChatMs(0L);
        summary.setCurrentStreak(0);
        summary.setLongestStreak(0);
        vo.setSummary(summary);
        if (userId == null) {
            return vo;
        }

        List<UserStatsDaily> rows = userStatsDailyService.listByUser(userId);
        int targetYear = year != null ? year : LocalDate.now().getYear();
        TreeSet<LocalDate> activeDates = new TreeSet<>();
        for (UserStatsDaily row : rows) {
            long tokens = tokenSum(row);
            summary.setTotalTokens(summary.getTotalTokens() + tokens);
            if (tokens > summary.getPeakTokens()) {
                summary.setPeakTokens(tokens);
                summary.setPeakDate(toDateString(row.getStatDate()));
            }
            if (row.getChatDurationMs() != null && row.getChatDurationMs() > summary.getLongestChatMs()) {
                summary.setLongestChatMs(row.getChatDurationMs());
            }
            LocalDate date = toLocalDate(row.getStatDate());
            if (date != null && (tokens > 0 || (row.getCallCount() != null && row.getCallCount() > 0))) {
                activeDates.add(date);
            }
            if (date != null && date.getYear() == targetYear) {
                ContributionVO.DayPoint point = new ContributionVO.DayPoint();
                point.setDate(DAY_FMT.format(date));
                point.setTokens(tokens);
                point.setCalls(row.getCallCount() != null ? row.getCallCount() : 0);
                vo.getDays().add(point);
            }
        }
        summary.setLongestStreak(calcLongestStreak(activeDates));
        summary.setCurrentStreak(calcCurrentStreak(activeDates));
        return vo;
    }

    @Override
    public TokenTrendVO buildTokenTrend(Long userId, Integer days, String dimension) {
        TokenTrendVO vo = new TokenTrendVO();
        vo.setDimension("agent".equals(dimension) ? "agent" : "model");
        vo.setSeries(new ArrayList<>());
        if (userId == null) {
            return vo;
        }

        int rangeDays = days == null ? 7 : Math.max(1, Math.min(365, days));
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(rangeDays - 1L);

        List<AgentUsageLog> logs = listUserUsageLogs(userId, start);
        // key → (date → [tokens, calls])；key 为模型名或智能体ID。
        // 模型维度只统计已记录模型名的日志（模型字段上线前的老数据不回填、不猜测）
        if (!"agent".equals(vo.getDimension())) {
            logs.removeIf(row -> row.getModel() == null || row.getModel().isBlank());
        }
        Map<String, Map<LocalDate, long[]>> grouped = new LinkedHashMap<>();
        for (AgentUsageLog row : logs) {
            LocalDate date = toLocalDate(row.getCreateTime());
            if (date == null || date.isBefore(start)) {
                continue;
            }
            String key = "agent".equals(vo.getDimension())
                    ? String.valueOf(row.getAgentId())
                    : (row.getModel() == null || row.getModel().isBlank() ? "未知模型" : row.getModel());
            grouped.computeIfAbsent(key, k -> new HashMap<>())
                    .computeIfAbsent(date, d -> new long[2]);
            long[] bucket = grouped.get(key).get(date);
            bucket[0] += tokenSum(row);
            bucket[1] += 1;
        }

        // 序列按总量排序，超出上限并入"其他"；群聊智能体不进图表（群聊统计实际对话的成员智能体）
        Map<Long, AgentDisplayResolver.Display> agentNames = "agent".equals(vo.getDimension())
                ? agentDisplayResolver.resolve(grouped.keySet().stream()
                        .map(this::parseIdOrNull).filter(java.util.Objects::nonNull).toList())
                : Map.of();
        if ("agent".equals(vo.getDimension())) {
            grouped.keySet().removeIf(key -> {
                Long id = parseIdOrNull(key);
                AgentDisplayResolver.Display display = id != null ? agentNames.get(id) : null;
                return display != null && display.isGroup();
            });
        }
        List<SeriesAgg> ranked = grouped.entrySet().stream()
                .map(e -> new SeriesAgg(e.getKey(),
                        e.getValue().values().stream().mapToLong(b -> b[0]).sum(),
                        e.getValue().values().stream().mapToLong(b -> b[1]).sum(),
                        e.getValue()))
                .sorted(Comparator.comparingLong(SeriesAgg::totalTokens).reversed())
                .toList();

        List<LocalDate> allDates = start.datesUntil(today.plusDays(1)).toList();
        List<SeriesAgg> top = ranked.subList(0, Math.min(TREND_SERIES_LIMIT, ranked.size()));
        long restTokens = ranked.stream().skip(TREND_SERIES_LIMIT).mapToLong(SeriesAgg::totalTokens).sum();
        long restCalls = ranked.stream().skip(TREND_SERIES_LIMIT).mapToLong(SeriesAgg::totalCalls).sum();

        for (SeriesAgg agg : top) {
            vo.getSeries().add(buildSeries(agg.key(), agg, allDates, agentNames));
        }
        if (restTokens > 0) {
            SeriesAgg rest = new SeriesAgg("-1", restTokens, restCalls, Map.of());
            vo.getSeries().add(buildSeries("-1", rest, allDates,
                    Map.of(-1L, new AgentDisplayResolver.Display("其他", null, null, null))));
        }
        return vo;
    }

    /** 趋势序列聚合中间态：key（模型名/智能体ID）→ 总量/调用数/逐日分桶 */
    private record SeriesAgg(String key, long totalTokens, long totalCalls, Map<LocalDate, long[]> byDate) {
    }

    private TokenTrendVO.Series buildSeries(String key, SeriesAgg agg, List<LocalDate> allDates,
                                            Map<Long, AgentDisplayResolver.Display> agentNames) {
        TokenTrendVO.Series series = new TokenTrendVO.Series();
        series.setId(key);
        if ("-1".equals(key)) {
            series.setName("其他");
        } else {
            Long agentId = parseIdOrNull(key);
            AgentDisplayResolver.Display display = agentId != null ? agentNames.get(agentId) : null;
            if (display != null) {
                series.setName(display.name());
                series.setAvatar(display.avatar());
            } else {
                series.setName(key);
                series.setAvatar(null);
            }
        }
        series.setTotalTokens(agg.totalTokens());
        series.setTotalCalls(agg.totalCalls());
        List<TokenTrendVO.Point> points = new ArrayList<>();
        for (LocalDate date : allDates) {
            TokenTrendVO.Point point = new TokenTrendVO.Point();
            point.setDate(DAY_FMT.format(date));
            long[] bucket = agg.byDate().get(date);
            point.setTokens(bucket != null ? bucket[0] : 0L);
            points.add(point);
        }
        series.setPoints(points);
        return series;
    }

    private Long parseIdOrNull(String key) {
        try {
            return Long.valueOf(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<AgentUsageLog> listUserUsageLogs(Long userId) {
        return agentUsageLogService.list(new LambdaQueryWrapper<AgentUsageLog>()
                .eq(AgentUsageLog::getUserId, userId));
    }

    private List<AgentUsageLog> listUserUsageLogs(Long userId, LocalDate start) {
        Date startDate = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        return agentUsageLogService.list(new LambdaQueryWrapper<AgentUsageLog>()
                .eq(AgentUsageLog::getUserId, userId)
                .ge(AgentUsageLog::getCreateTime, startDate));
    }

    /** 当前连续天数：今天未活跃从昨天回数（今天还没聊不该清零连击） */
    private int calcCurrentStreak(TreeSet<LocalDate> activeDates) {
        if (activeDates.isEmpty()) {
            return 0;
        }
        LocalDate cursor = LocalDate.now();
        if (!activeDates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (activeDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private int calcLongestStreak(TreeSet<LocalDate> activeDates) {
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate date : activeDates) {
            run = prev != null && ChronoUnit.DAYS.between(prev, date) == 1 ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = date;
        }
        return longest;
    }

    private long tokenSum(UserStatsDaily row) {
        return (row.getInputTokens() != null ? row.getInputTokens() : 0)
                + (row.getOutputTokens() != null ? row.getOutputTokens() : 0);
    }

    private long tokenSum(AgentUsageLog row) {
        return (row.getInputTokens() != null ? row.getInputTokens() : 0)
                + (row.getOutputTokens() != null ? row.getOutputTokens() : 0);
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String toDateString(Date date) {
        LocalDate localDate = toLocalDate(date);
        return localDate == null ? null : DAY_FMT.format(localDate);
    }

    private UserProfileVO zeroProfile(UserProfileVO vo) {
        vo.setRegisterDays(0L);
        vo.setActiveDays(0L);
        vo.setTotalTurns(0L);
        vo.setTotalConversations(0L);
        vo.setTotalMessages(0L);
        vo.setTotalTokens(0L);
        vo.setAvgDailyTokens(0L);
        vo.setFavoriteModelTokens(0L);
        vo.setFavoriteAgentTokens(0L);
        vo.setAgentCount(0L);
        vo.setKnowledgeCount(0L);
        vo.setMcpCount(0L);
        vo.setSkillCount(0L);
        vo.setToolCallCount(0L);
        vo.setRetrievalCount(0L);
        vo.setSkillUseCount(0L);
        vo.setActivityCount(0L);
        return vo;
    }
}
