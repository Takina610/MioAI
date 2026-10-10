package com.mio.ai.resource.controller.knowledge;

import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.resource.model.dto.githubimport.GithubImportPreviewRequest;
import com.mio.ai.resource.model.dto.githubimport.GithubImportRequest;
import com.mio.ai.resource.model.vo.knowledge.GithubImportItemVO;
import com.mio.ai.resource.model.vo.knowledge.GithubPreviewVO;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.service.knowledge.GithubImportService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseCreateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/4/5
 * @description: 知识库创建控制器
 */
@RestController
@RequestMapping("/knowledge-bases/create")
public class KnowledgeBaseCreateController {

    @Autowired
    private KnowledgeBaseCreateService knowledgeBaseCreateService;

    @Autowired
    private GithubImportService githubImportService;

    @Autowired
    private RedisComponent redisComponent;

    /**
     * 上传文件到R2
     * @param kbId 知识库ID
     * @param files 文件列表
     * @param request HTTP请求
     * @return 上传结果
     */
    @PostMapping("/upload/{kbId}")
    @LogInfo
    @CacheEvict(value = "knowledgeBases", allEntries = true)
    public BaseResponse<List<Map<String, Object>>> uploadFiles(
            @PathVariable Long kbId,
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        List<Map<String, Object>> result = knowledgeBaseCreateService.uploadFiles(kbId, userId, files);
        return ResultUtils.success(result);
    }

    /**
     * 向量化处理文件（SSE）
     * @param kbId 知识库ID
     * @param token 认证token
     * @return SSE发射器
     */
    @GetMapping("/vectorize/{kbId}")
    @LogInfo
    @CacheEvict(value = "knowledgeBases", allEntries = true)
    public SseEmitter vectorizeFiles(
            @PathVariable Long kbId,
            @RequestParam String token) {
        Long userId = redisComponent.getUserId(token);
        SseEmitter emitter = new SseEmitter(300000L);
        knowledgeBaseCreateService.vectorizeFiles(kbId, userId, emitter);
        return emitter;
    }

    /**
     * 取消创建并清理资源
     * @param kbId 知识库ID
     * @param request HTTP请求
     * @return 成功响应
     */
    @PostMapping("/cancel/{kbId}")
    @LogInfo
    public BaseResponse<Boolean> cancelCreation(@PathVariable Long kbId, HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        knowledgeBaseCreateService.cancelCreation(kbId, userId);
        return ResultUtils.success(true);
    }

    /**
     * 解析 GitHub 链接，返回可导入文件清单
     */
    @PostMapping("/github/preview")
    @LogInfo
    public BaseResponse<GithubPreviewVO> previewGithubImport(
            @Valid @RequestBody GithubImportPreviewRequest previewRequest,
            HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        GithubPreviewVO preview = githubImportService.preview(userId, previewRequest.getUrl());
        return ResultUtils.success(preview);
    }

    /**
     * 导入选中的 GitHub 文件到知识库（下载→R2→文档记录），向量化沿用既有流程
     */
    @PostMapping("/github/import")
    @LogInfo
    @CacheEvict(value = "knowledgeBases", allEntries = true)
    public BaseResponse<List<GithubImportItemVO>> importGithubFiles(
            @Valid @RequestBody GithubImportRequest importRequest,
            HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        List<GithubImportItemVO> result = githubImportService.importToKnowledgeBase(
                importRequest.getKbId(), userId, importRequest.getUrl(), importRequest.getPaths());
        return ResultUtils.success(result);
    }
}
