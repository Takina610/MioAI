package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.vo.DocumentVO;
import com.mio.ai.customagent.service.DocumentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档接口
 */
@Slf4j
@RestController
@RequestMapping("/documents")
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping
    public BaseResponse<Long> addDocument(@RequestBody DocumentAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Long id = documentService.addDocument(request, userId);
        return ResultUtils.success(id);
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteDocument(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = documentService.deleteDocument(id, userId);
        return ResultUtils.success(result);
    }

    @GetMapping("/{id}")
    public BaseResponse<DocumentVO> getDocument(@PathVariable Long id) {
        DocumentVO document = documentService.getDocumentById(id);
        return ResultUtils.success(document);
    }

    @PostMapping("/list")
    public BaseResponse<Page<DocumentVO>> listDocuments(@RequestBody DocumentQueryRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Page<DocumentVO> page = documentService.queryDocuments(request, userId);
        return ResultUtils.success(page);
    }
}
