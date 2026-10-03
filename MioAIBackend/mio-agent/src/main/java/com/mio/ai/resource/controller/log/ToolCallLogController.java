package com.mio.ai.resource.controller.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.log.ToolCallLogQueryRequest;
import com.mio.ai.resource.model.vo.log.ToolCallLogVO;
import com.mio.ai.resource.service.log.ToolCallLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志接口
 */
@Slf4j
@RestController
@RequestMapping("/tool-call-logs")
public class ToolCallLogController {

    @Resource
    private ToolCallLogService toolCallLogService;

    @PostMapping("/list")
    public BaseResponse<Page<ToolCallLogVO>> listToolCallLogs(@RequestBody ToolCallLogQueryRequest request) {
        Page<ToolCallLogVO> page = toolCallLogService.queryToolCallLogs(request);
        return ResultUtils.success(page);
    }

    @GetMapping("/count/{toolId}")
    public BaseResponse<Long> countByToolId(@PathVariable Long toolId) {
        Long count = toolCallLogService.countByToolId(toolId);
        return ResultUtils.success(count);
    }
}
