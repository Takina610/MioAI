package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.vo.DocumentVO;
import com.mio.ai.customagent.model.vo.SimilaritySearchResultVO;
import com.mio.ai.customagent.service.DocumentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @Resource
    private R2Util r2Util;

    @Autowired
    VectorStore vectorStore;

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

    @GetMapping("/preview/{id}")
    public void previewDocument(@PathVariable Long id, HttpServletResponse response) {
        try {
            DocumentVO document = documentService.getDocumentById(id);
            if (document == null || document.getFilePath() == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            byte[] fileContent = r2Util.downloadFile(document.getFilePath());
            String contentType = getContentType(document.getFileType());
            String fileName = document.getFileName();

            response.setContentType(contentType);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.setContentLength(fileContent.length);
            response.getOutputStream().write(fileContent);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("预览文件失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/similaritySearch/{content}/{threshold}/{topK}")
    public BaseResponse<List<SimilaritySearchResultVO>> similaritySearch(@PathVariable Double threshold, @PathVariable String content, @PathVariable Integer topK) {
        SearchRequest searchRequest = SearchRequest
                .builder().query(content)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        
        List<SimilaritySearchResultVO> results = documents.stream().map(doc -> {
            SimilaritySearchResultVO vo = new SimilaritySearchResultVO();
            vo.setId(doc.getId());
            vo.setText(doc.getText());
            vo.setScore(doc.getScore());
            
            String fileName = "未知文档";
            try {
                Long docId = parseDocIdFromVectorId(doc.getId());
                if (docId != null) {
                    com.mio.ai.customagent.model.entity.Document document = documentService.getById(docId);
                    if (document != null && document.getFileName() != null) {
                        fileName = document.getFileName();
                    }
                }
            } catch (Exception e) {
                log.warn("获取文档名称失败: {}", doc.getId(), e);
            }
            vo.setFileName(fileName);
            return vo;
        }).toList();
        
        return ResultUtils.success(results);
    }

    private Long parseDocIdFromVectorId(String vectorId) {
        if (vectorId == null || !vectorId.startsWith("doc_")) {
            return null;
        }
        try {
            String[] parts = vectorId.split("_");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (NumberFormatException e) {
            log.warn("解析文档ID失败: {}", vectorId);
        }
        return null;
    }

    private String getContentType(String fileType) {
        if (fileType == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return switch (fileType.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "txt" -> MediaType.TEXT_PLAIN_VALUE;
            case "md" -> MediaType.TEXT_MARKDOWN_VALUE;
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
