package com.mio.ai.resource.controller.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.log.AgentUsageLogQueryRequest;
import com.mio.ai.resource.model.vo.log.AgentUsageLogVO;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志接口
 */
@Slf4j
@RestController
@RequestMapping("/agent-usage-logs")
public class AgentUsageLogController {

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @PostMapping("/list")
    public BaseResponse<Page<AgentUsageLogVO>> listUsageLogs(@RequestBody AgentUsageLogQueryRequest request) {
        Page<AgentUsageLogVO> page = agentUsageLogService.queryUsageLogs(request);
        return ResultUtils.success(page);
    }

    @GetMapping("/count/{agentId}")
    public BaseResponse<Long> countByAgentId(@PathVariable Long agentId) {
        Long count = agentUsageLogService.countByAgentId(agentId);
        return ResultUtils.success(count);
    }

    @GetMapping("/tokens/{agentId}")
    public BaseResponse<Long> sumTokensByAgentId(@PathVariable Long agentId) {
        Long tokens = agentUsageLogService.sumTokensByAgentId(agentId);
        return ResultUtils.success(tokens);
    }
}
