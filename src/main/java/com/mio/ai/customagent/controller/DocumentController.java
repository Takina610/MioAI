package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.vo.DocumentVO;
import com.mio.ai.customagent.service.DocumentService;
import jakarta.annotation.Resource;
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

    @PostMapping
    public BaseResponse<Long> addDocument(@RequestBody DocumentAddRequest request) {
        Long id = documentService.addDocument(request);
        return ResultUtils.success(id);
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteDocument(@PathVariable Long id) {
        boolean result = documentService.deleteDocument(id);
        return ResultUtils.success(result);
    }

    @GetMapping("/{id}")
    public BaseResponse<DocumentVO> getDocument(@PathVariable Long id) {
        DocumentVO document = documentService.getDocumentById(id);
        return ResultUtils.success(document);
    }

    @PostMapping("/list")
    public BaseResponse<Page<DocumentVO>> listDocuments(@RequestBody DocumentQueryRequest request) {
        Page<DocumentVO> page = documentService.queryDocuments(request);
        return ResultUtils.success(page);
    }
}
