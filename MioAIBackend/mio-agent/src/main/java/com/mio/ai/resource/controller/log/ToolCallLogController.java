package com.mio.ai.resource.controller.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.log.ToolCallLogQueryRequest;
import com.mio.ai.resource.model.vo.log.ToolCallLogVO;
import com.mio.ai.resource.service.log.ToolCallLogService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志接口
 * <p>含工具入参与产出等用户数据：必须登录，且只能查本人记录。
 */
@Slf4j
@RestController
@RequestMapping("/tool-call-logs")
public class ToolCallLogController {

    @Resource
    private ToolCallLogService toolCallLogService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping("/list")
    public BaseResponse<Page<ToolCallLogVO>> listToolCallLogs(@RequestBody ToolCallLogQueryRequest request,
                                                                HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Page<ToolCallLogVO> page = toolCallLogService.queryToolCallLogs(request, userId);
        return ResultUtils.success(page);
    }

    @GetMapping("/count/{toolId}")
    public BaseResponse<Long> countByToolId(@PathVariable Long toolId, HttpServletRequest httpRequest) {
        redisComponent.getUserId(httpRequest.getHeader("token"));
        Long count = toolCallLogService.countByToolId(toolId);
        return ResultUtils.success(count);
    }
}
