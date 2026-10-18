package com.mio.ai.resource.controller.log;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.vo.log.UsageAnalyticsVO;
import com.mio.ai.resource.model.vo.log.UsageDayDetailVO;
import com.mio.ai.resource.model.vo.log.UsageStatsVO;
import com.mio.ai.resource.service.log.UsageStatsService;
import com.mio.ai.resource.service.log.impl.UsageAnalyticsServiceImpl;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 使用记录统计接口（当前用户）
 */
@Slf4j
@RestController
@RequestMapping("/usage-stats")
public class UsageStatsController {

    @Resource
    private UsageStatsService usageStatsService;

    @Resource
    private UsageAnalyticsServiceImpl usageAnalyticsService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private HttpServletRequest request;

    @GetMapping("/{type}")
    public BaseResponse<UsageStatsVO> getStats(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        Long userId = redisComponent.getUserId(request.getHeader("token"));
        UsageStatsVO stats = usageStatsService.buildStats(type, startTime, endTime, userId);
        if (stats == null) {
            return (BaseResponse<UsageStatsVO>) ResultUtils.error(400, "不支持的统计类型");
        }
        return ResultUtils.success(stats);
    }

    /** 使用去向 / 使用构成 / 使用明细（当前用户） */
    @GetMapping("/analytics/{type}")
    public BaseResponse<UsageAnalyticsVO> getAnalytics(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        Long userId = redisComponent.getUserId(request.getHeader("token"));
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return ResultUtils.success(
                usageAnalyticsService.buildAnalytics(type, start, end, userId, null, false));
    }

    /** 日历热力图点击某天：当日明细（当前用户） */
    @GetMapping("/day-detail")
    public BaseResponse<UsageDayDetailVO> getDayDetail(
            @RequestParam String date,
            @RequestParam(required = false, defaultValue = "agent") String type) {

        Long userId = redisComponent.getUserId(request.getHeader("token"));
        return ResultUtils.success(usageAnalyticsService.buildDayDetail(userId, type, date));
    }
}
