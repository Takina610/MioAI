package com.mio.ai.admin.controller;

import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.vo.log.UsageStatsVO;
import com.mio.ai.customagent.service.log.UsageStatsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 管理员使用记录统计接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/usage-stats")
public class UsageStatsAdminController {

    @Resource
    private UsageStatsService usageStatsService;

    @GetMapping("/{type}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<UsageStatsVO> getAdminStats(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        UsageStatsVO stats = usageStatsService.buildStats(type, startTime, endTime, null);
        if (stats == null) {
            return (BaseResponse<UsageStatsVO>) ResultUtils.error(400, "不支持的统计类型");
        }
        return ResultUtils.success(stats);
    }
}
