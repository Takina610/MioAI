package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseAddRequest;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseQueryRequest;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseUpdateRequest;
import com.mio.ai.customagent.model.vo.KnowledgeBaseVO;
import com.mio.ai.customagent.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库接口
 */
@Slf4j
@RestController
@RequestMapping("/knowledge-bases")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping
    public BaseResponse<Long> addKnowledgeBase(@RequestBody KnowledgeBaseAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Long id = knowledgeBaseService.addKnowledgeBase(request, userId);
        return ResultUtils.success(id);
    }

    @PutMapping
    public BaseResponse<Boolean> updateKnowledgeBase(@RequestBody KnowledgeBaseUpdateRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = knowledgeBaseService.updateKnowledgeBase(request, userId);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/{id:\\d+}")
    public BaseResponse<Boolean> deleteKnowledgeBase(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = knowledgeBaseService.deleteKnowledgeBase(id, userId);
        return ResultUtils.success(result);
    }

    @GetMapping("/market")
    public BaseResponse<Page<KnowledgeBaseVO>> getMarketKnowledgeBases(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "12") long size) {
        Page<KnowledgeBaseVO> page = knowledgeBaseService.getPublicKnowledgeBases(current, size);
        return ResultUtils.success(page);
    }

    @GetMapping("/{id:\\d+}")
    public BaseResponse<KnowledgeBaseVO> getKnowledgeBase(@PathVariable Long id) {
        KnowledgeBaseVO kb = knowledgeBaseService.getKnowledgeBaseById(id);
        return ResultUtils.success(kb);
    }

    @PostMapping("/list")
    public BaseResponse<Page<KnowledgeBaseVO>> listKnowledgeBases(@RequestBody KnowledgeBaseQueryRequest request,
                                                                  HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        request.setUserId(userId);
        Page<KnowledgeBaseVO> page = knowledgeBaseService.queryKnowledgeBases(request);
        return ResultUtils.success(page);
    }
}
