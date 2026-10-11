package com.mio.ai.resource.controller.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.document.DocumentAddRequest;
import com.mio.ai.resource.model.dto.document.DocumentQueryRequest;
import com.mio.ai.resource.model.dto.document.SimilaritySearchRequest;
import com.mio.ai.resource.model.vo.knowledge.DocumentChunkVO;
import com.mio.ai.resource.model.vo.knowledge.DocumentVO;
import com.mio.ai.resource.model.vo.knowledge.SimilaritySearchResultVO;
import com.mio.ai.framework.rag.KnowledgeChunkQueryClient;
import com.mio.ai.framework.rag.KnowledgeRetrievalResult;
import com.mio.ai.framework.rag.RetrievalConfig;
import com.mio.ai.framework.rag.RetrievalOutcome;
import com.mio.ai.resource.service.knowledge.DocumentService;
import com.mio.ai.resource.service.knowledge.KnowledgeRetrievalService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.service.security.AccessGuardService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/documents")
public class DocumentController {

    @Resource
    private DocumentService documentService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Resource
    private AccessGuardService accessGuardService;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private R2Util r2Util;

    @Resource
    private KnowledgeChunkQueryClient chunkQueryClient;

    @PostMapping
    @CacheEvict(value = "knowledgeBases", allEntries = true)
    public BaseResponse<Long> addDocument(@Valid @RequestBody DocumentAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Long id = documentService.addDocument(request, userId);
        return ResultUtils.success(id);
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "knowledgeBases", allEntries = true)
    public BaseResponse<Boolean> deleteDocument(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = documentService.deleteDocument(id, userId);
        return ResultUtils.success(result);
    }

    /**
     * 获取文档详情（所有者或公开知识库内文档可见）
     */
    @GetMapping("/{id}")
    public BaseResponse<DocumentVO> getDocument(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        DocumentVO document = documentService.getDocumentById(id, userId);
        return ResultUtils.success(document);
    }

    @PostMapping("/list")
    public BaseResponse<Page<DocumentVO>> listDocuments(@RequestBody DocumentQueryRequest request, HttpServletRequest httpRequest) {
        // userId 一律以登录态为准，不接受前端传入，防止越权枚举他人文档
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        request.setUserId(userId);
        Page<DocumentVO> page = documentService.queryDocuments(request, userId);
        return ResultUtils.success(page);
    }

    /**
     * 在线预览文档原文件（所有者或公开知识库内文档可预览）
     */
    @GetMapping("/preview/{id}")
    public void previewDocument(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
            Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
            DocumentVO document = documentService.getDocumentById(id, userId);
            if (document == null || document.getFilePath() == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            byte[] fileContent = r2Util.downloadFile(document.getFilePath());
            String contentType = getContentType(document.getFileType());
            String fileName = document.getFileName();

            response.setContentType(contentType);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            response.setContentLength(fileContent.length);
            response.getOutputStream().write(fileContent);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("预览文件失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 命中测试：向量检索限定在用户有权访问的知识库范围内
     *
     * @param kbId 可选；传入时只在该知识库内检索（需为所有者或公开知识库），
     *             不传时检索"本人全部知识库 + 公开知识库"
     */
    @GetMapping("/similaritySearch/{content}/{threshold}/{topK}")
    public BaseResponse<List<SimilaritySearchResultVO>> similaritySearch(
            @PathVariable Double threshold,
            @PathVariable String content,
            @PathVariable Integer topK,
            @RequestParam(required = false) Long kbId,
            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));

        List<Long> searchableKbIds;
        if (kbId != null) {
            accessGuardService.checkKbReadable(kbId, userId);
            searchableKbIds = List.of(kbId);
        } else {
            searchableKbIds = listSearchableKbIds(userId);
            if (searchableKbIds.isEmpty()) {
                return ResultUtils.success(List.of());
            }
        }

        List<KnowledgeRetrievalResult> results =
                knowledgeRetrievalService.retrieve(searchableKbIds, content, topK, threshold, false);

        return ResultUtils.success(results.stream().map(r -> {
            SimilaritySearchResultVO vo = new SimilaritySearchResultVO();
            vo.setId(r.getChunkId());
            vo.setText(r.getText());
            vo.setScore(r.getScore());
            vo.setFileName(r.getFileName() != null ? r.getFileName() : "未知文档");
            return vo;
        }).toList());
    }

    /**
     * 命中测试（POST body 版）：支持长查询与完整检索配置（方式/重排），
     * 检索范围限定在用户有权访问的知识库内
     */
    @PostMapping("/similaritySearch")
    public BaseResponse<List<SimilaritySearchResultVO>> similaritySearchByBody(
            @Valid @RequestBody SimilaritySearchRequest request,
            HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));

        List<Long> searchableKbIds;
        if (request.getKbId() != null) {
            accessGuardService.checkKbReadable(request.getKbId(), userId);
            searchableKbIds = List.of(request.getKbId());
        } else {
            searchableKbIds = listSearchableKbIds(userId);
            if (searchableKbIds.isEmpty()) {
                return ResultUtils.success(List.of());
            }
        }

        RetrievalConfig config = RetrievalConfig.defaults();
        if (request.getTopK() != null && request.getTopK() > 0) {
            config.setTopK(Math.min(request.getTopK(), 50));
        }
        if (request.getThreshold() != null && request.getThreshold() >= 0 && request.getThreshold() <= 1) {
            config.setThreshold(request.getThreshold());
        }
        if (request.getMethod() != null && !request.getMethod().isBlank()) {
            config.setMethod("vector".equalsIgnoreCase(request.getMethod().trim())
                    ? RetrievalConfig.METHOD_VECTOR : RetrievalConfig.METHOD_HYBRID);
        }
        if (request.getEnableRerank() != null) {
            config.setEnableRerank(request.getEnableRerank());
        }

        RetrievalOutcome outcome = knowledgeRetrievalService.retrieveDetailed(
                searchableKbIds, request.getQuery(), config);
        return ResultUtils.success(outcome.results().stream()
                .map(DocumentController::toSearchVO)
                .toList());
    }

    /**
     * 查看文档解析出的全部分块（切片），按 chunkIndex 升序
     */
    @GetMapping("/{id:\\d+}/chunks")
    public BaseResponse<List<DocumentChunkVO>> listChunks(@PathVariable Long id,
                                                          HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        DocumentVO document = documentService.getDocumentById(id, userId);
        if (document == null) {
            return ResultUtils.success(List.of());
        }
        List<KnowledgeChunkQueryClient.ChunkRow> rows = chunkQueryClient.listByDocId(id);
        return ResultUtils.success(rows.stream().map(row -> {
            DocumentChunkVO vo = new DocumentChunkVO();
            vo.setChunkId(row.id());
            vo.setChunkIndex(row.chunkIndex());
            vo.setContent(row.content());
            vo.setLength(row.content() != null ? row.content().length() : 0);
            vo.setFileName(row.fileName() != null ? row.fileName() : document.getFileName());
            return vo;
        }).toList());
    }

    /**
     * 单文档重新解析：状态重置为待处理，前端随后打开 vectorize SSE 流水线执行
     */
    @PostMapping("/{id:\\d+}/revectorize")
    @CacheEvict(value = "knowledgeBases", allEntries = true)
    public BaseResponse<Boolean> revectorizeDocument(@PathVariable Long id,
                                                     HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        return ResultUtils.success(documentService.resetForRevectorize(id, userId));
    }

    private static SimilaritySearchResultVO toSearchVO(KnowledgeRetrievalResult r) {
        SimilaritySearchResultVO vo = new SimilaritySearchResultVO();
        vo.setId(r.getChunkId());
        vo.setText(r.getText());
        vo.setScore(r.getScore());
        vo.setFileName(r.getFileName() != null ? r.getFileName() : "未知文档");
        vo.setDocId(r.getDocId());
        Object chunkIndex = r.getMetadata() != null ? r.getMetadata().get("chunkIndex") : null;
        if (chunkIndex instanceof Number number) {
            vo.setChunkIndex(number.intValue());
        }
        Object matchType = r.getMetadata() != null ? r.getMetadata().get("matchType") : null;
        vo.setMatchType(matchType != null ? String.valueOf(matchType) : null);
        return vo;
    }

    /**
     * 可检索的知识库 = 本人创建的知识库 + 公开知识库
     */
    private List<Long> listSearchableKbIds(Long userId) {
        List<Long> ownKbIds = knowledgeBaseService.list().stream()
                .filter(kb -> userId != null && userId.equals(kb.getUserId()))
                .map(KnowledgeBase::getId)
                .toList();
        List<Long> publicKbIds = knowledgeBaseService.list().stream()
                .filter(kb -> Integer.valueOf(1).equals(kb.getIsPublic()))
                .map(KnowledgeBase::getId)
                .toList();
        return java.util.stream.Stream.concat(ownKbIds.stream(), publicKbIds.stream())
                .distinct()
                .toList();
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
