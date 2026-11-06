package com.mio.ai.resource.controller.knowledge;

import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.api.BaseResponse;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseCreateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
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

    /** 知识库归属校验（进度/取消等按 kbId 操作的入口共用） */
    @Autowired
    private com.mio.ai.resource.service.security.AccessGuardService accessGuardService;

    /** 向量化进度快照（任务在独立线程池跑，与 SSE 连接无关） */
    @Autowired
    private com.mio.ai.bot.service.resource.KnowledgeIngestService knowledgeIngestService;

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
            @RequestParam String token,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        Long userId = redisComponent.getUserId(token);
        // 超时对齐服务端 30 分钟的批次等待：大文件多分块的向量化不应中途掐断 SSE
        SseEmitter emitter = new SseEmitter(1800000L);
        knowledgeBaseCreateService.vectorizeFiles(kbId, userId, emitter, force);
        return emitter;
    }

    /**
     * 查询当前用户仍在途的向量化任务（有 status 0/1 文档的知识库），
     * 页面刷新/重开后由前端调用并自动恢复到向量化进度界面
     */
    @GetMapping("/active")
    public BaseResponse<List<Map<String, Object>>> listActiveVectorizations(HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        return ResultUtils.success(knowledgeBaseCreateService.listActiveVectorizations(userId));
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
     * 单个知识库的向量化精确进度（对话内建库场景：聊天流没有第二条 SSE 通道，改轮询）。
     * <p>无任务时返回 running=false；任务结束返回终态与失败/取消标记。
     */
    @GetMapping("/progress/{kbId}")
    public BaseResponse<Map<String, Object>> vectorizeProgress(@PathVariable Long kbId,
                                                               HttpServletRequest request) {
        Long userId = redisComponent.getUserId(request.getHeader("token"));
        // 归属校验：进度里含文件名，不能让任何人凭 kbId 读别人知识库的进度
        accessGuardService.checkKbOwner(kbId, userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kbId", kbId);
        var progress = knowledgeIngestService.progress(kbId);
        body.put("running", knowledgeIngestService.isVectorizing(kbId));
        if (progress == null) {
            body.put("total", 0);
            body.put("current", 0);
            body.put("failed", 0);
            body.put("done", true);
            body.put("message", "");
            return ResultUtils.success(body);
        }
        body.put("total", progress.total);
        body.put("current", progress.current.get());
        body.put("failed", progress.failedCount.get());
        body.put("done", progress.done);
        body.put("failedAll", progress.failed);
        body.put("cancelled", progress.cancelled);
        body.put("message", progress.message == null ? "" : progress.message);
        body.put("fileName", progress.fileName);
        body.put("errorMessage", progress.errorMessage);
        return ResultUtils.success(body);
    }
}
