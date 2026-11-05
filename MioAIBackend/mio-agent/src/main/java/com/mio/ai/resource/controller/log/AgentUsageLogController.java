package com.mio.ai.resource.controller.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.api.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.log.AgentUsageLogQueryRequest;
import com.mio.ai.resource.model.vo.log.AgentUsageLogVO;
import com.mio.ai.resource.service.log.AgentUsageLogService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志接口
 * <p>日志含提问与用量等用户数据：必须登录，且只查本人记录（管理员走 /admin/logs 系列）。
 */
@Slf4j
@RestController
@RequestMapping("/agent-usage-logs")
public class AgentUsageLogController {

    @Resource
    private AgentUsageLogService agentUsageLogService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private com.mio.ai.resource.service.security.AccessGuardService accessGuardService;

    @PostMapping("/list")
    public BaseResponse<Page<AgentUsageLogVO>> listUsageLogs(@RequestBody AgentUsageLogQueryRequest request,
                                                             HttpServletRequest httpRequest) {
        // userId 一律以登录态为准，不接受前端传入：否则可枚举全站用量
        request.setUserId(redisComponent.getUserId(httpRequest.getHeader("token")));
        Page<AgentUsageLogVO> page = agentUsageLogService.queryUsageLogs(request);
        return ResultUtils.success(page);
    }

    /** 计数：要求登录，且该智能体对调用者可读（自有/公开/内置），否则拒绝 */
    @GetMapping("/count/{agentId}")
    public BaseResponse<Long> countByAgentId(@PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentReadable(agentId, userId);
        Long count = agentUsageLogService.countByAgentId(agentId);
        return ResultUtils.success(count);
    }

    @GetMapping("/tokens/{agentId}")
    public BaseResponse<Long> sumTokensByAgentId(@PathVariable Long agentId, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentReadable(agentId, userId);
        Long tokens = agentUsageLogService.sumTokensByAgentId(agentId);
        return ResultUtils.success(tokens);
    }
}
