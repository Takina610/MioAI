package com.mio.ai.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.admin.model.vo.AdminOverviewChartsVO;
import com.mio.ai.admin.model.vo.OverviewStatsVO;
import com.mio.ai.admin.service.AdminOverviewChartsService;
import com.mio.ai.bot.mapper.AgentMessageMapper;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.bot.mapper.ChatFileMapper;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.mapper.log.AgentUsageLogMapper;
import com.mio.ai.resource.mapper.task.AgentBackgroundTaskMapper;
import com.mio.ai.resource.model.entity.AgentBackgroundTask;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.mcp.McpToolService;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 管理后台总览统计接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/overview")
public class OverviewAdminController {

    @Resource
    private UserService userService;

    @Resource
    private AgentService agentService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private McpToolService mcpToolService;

    @Resource
    private SkillService skillService;

    @Resource
    private ChatConversationDOMapper chatConversationMapper;

    @Resource
    private AgentMessageMapper agentMessageMapper;

    @Resource
    private ChatFileMapper chatFileMapper;

    @Resource
    private AgentUsageLogMapper agentUsageLogMapper;

    @Resource
    private AgentBackgroundTaskMapper backgroundTaskMapper;

    @Resource
    private AdminOverviewChartsService adminOverviewChartsService;

    @GetMapping("/stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<OverviewStatsVO> getOverviewStats() {
        OverviewStatsVO vo = new OverviewStatsVO();
        vo.setUsers(userService.count());
        vo.setAgents(agentService.count());
        vo.setKnowledgeBases(knowledgeBaseService.count());
        vo.setMcpTools(mcpToolService.count());
        vo.setSkills(skillService.count());
        vo.setConversations(chatConversationMapper.selectCount(null));
        vo.setMessages(agentMessageMapper.selectCount(null));
        vo.setChatFiles(chatFileMapper.selectCount(null));

        String today = LocalDate.now().toString();
        vo.setTodayTurns(agentUsageLogMapper.selectCount(
                new QueryWrapper<AgentUsageLog>().apply("DATE(create_time) = {0}", today)));
        vo.setTodayFailedTurns(agentUsageLogMapper.selectCount(
                new QueryWrapper<AgentUsageLog>().eq("status", 0).apply("DATE(create_time) = {0}", today)));

        Map<String, Object> tokenSum = agentUsageLogMapper.selectMaps(new QueryWrapper<AgentUsageLog>()
                .select("IFNULL(SUM(input_tokens),0) AS inputTokens, IFNULL(SUM(output_tokens),0) AS outputTokens")
                .apply("DATE(create_time) = {0}", today)).stream().findFirst().orElse(Map.of());
        vo.setTodayInputTokens(((Number) tokenSum.getOrDefault("inputTokens", 0)).longValue());
        vo.setTodayOutputTokens(((Number) tokenSum.getOrDefault("outputTokens", 0)).longValue());

        vo.setRunningTasks(backgroundTaskMapper.selectCount(
                new QueryWrapper<AgentBackgroundTask>().eq("status", "running")));
        return ResultUtils.success(vo);
    }

    @GetMapping("/charts")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AdminOverviewChartsVO> getOverviewCharts(
            @RequestParam(required = false) Integer days) {
        return ResultUtils.success(adminOverviewChartsService.buildCharts(days));
    }
}
