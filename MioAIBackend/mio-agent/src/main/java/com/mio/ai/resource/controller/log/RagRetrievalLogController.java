package com.mio.ai.resource.controller.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.api.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.log.RagRetrievalLogQueryRequest;
import com.mio.ai.resource.model.vo.log.RagRetrievalLogVO;
import com.mio.ai.resource.service.log.RagRetrievalLogService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志接口
 * <p>检索日志含用户原始提问与知识库片段：必须登录，且只能查本人记录。
 */
@Slf4j
@RestController
@RequestMapping("/rag-retrieval-logs")
public class RagRetrievalLogController {

    @Resource
    private RagRetrievalLogService ragRetrievalLogService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping("/list")
    public BaseResponse<Page<RagRetrievalLogVO>> listRetrievalLogs(@RequestBody RagRetrievalLogQueryRequest request,
                                                                    HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Page<RagRetrievalLogVO> page = ragRetrievalLogService.queryRetrievalLogs(request, userId);
        return ResultUtils.success(page);
    }
}
