package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.log.RagRetrievalLogQueryRequest;
import com.mio.ai.customagent.model.vo.RagRetrievalLogVO;
import com.mio.ai.customagent.service.RagRetrievalLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志接口
 */
@Slf4j
@RestController
@RequestMapping("/rag-retrieval-logs")
public class RagRetrievalLogController {

    @Resource
    private RagRetrievalLogService ragRetrievalLogService;

    @PostMapping("/list")
    public BaseResponse<Page<RagRetrievalLogVO>> listRetrievalLogs(@RequestBody RagRetrievalLogQueryRequest request) {
        Page<RagRetrievalLogVO> page = ragRetrievalLogService.queryRetrievalLogs(request);
        return ResultUtils.success(page);
    }
}
