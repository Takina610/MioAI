package com.mio.ai.bot.controller;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.common.api.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话附件：用户上传 → SFTP 直落沙箱工作区 uploads/&lt;chatId&gt;/（Agent 文件工具可读）；
 * Agent 产出 outputs/&lt;chatId&gt;/ 由本控制器下载接口拉回给用户。
 * 与 /bot/chat 一致：游客可用（path 校验足矣，毕设单用户场景）。
 */
@Slf4j
@Validated
@RestController
public class AttachmentController {

    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

    @Resource
    private SandboxFileTransfer fileTransfer;

    @Resource
    private com.mio.ai.bot.service.AttachmentDeleteQueue deleteQueue;

    @Resource
    private com.mio.ai.bot.service.ChatFileService chatFileService;

    @Resource
    private com.mio.ai.user.utils.RedisComponent redisComponent;

    @Resource
    private com.mio.ai.bot.repository.ChatHistoryRepository chatHistoryRepository;

    /** 上传附件：返回 {path, name, size}，发送消息时随 attachments 参数带给 /bot/chat */
    @PostMapping("/bot/attachment")
    public BaseResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("chatId") @NotBlank @Size(max = 64) String chatId,
                                                    jakarta.servlet.http.HttpServletRequest request) {
        if (!fileTransfer.available()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱未启用，暂不支持上传附件");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单个文件不能超过 100MB");
        }
        // 归属校验：已有会话必须属于当前用户（游客会话不落库、无从比对，放行）
        Long uploaderId = resolveUserId(request);
        requireChatOwnership(chatId, uploaderId);
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "file");
        try {
            String path = fileTransfer.upload(chatId, originalName, file.getInputStream());
            try {
                // 台账登记：会话删除按它清理；失败不阻断上传（目录级删除按目录清理兜底）
                chatFileService.register(chatId, uploaderId, null,
                        com.mio.ai.bot.service.ChatFileService.KIND_UPLOAD, path, originalName, file.getSize());
            } catch (Exception e) {
                log.warn("附件登记失败（文件已上传，删除会话时按目录清理）: path={}, err={}", path, e.getMessage());
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path);
            data.put("name", originalName);
            data.put("size", file.getSize());
            return ResultUtils.success(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 带上堆栈：上传失败常见原因是沙箱连接/目录权限，只有 message 不够排障
            log.warn("附件上传失败, chatId={}, name={}", chatId, originalName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "附件上传失败，请重试");
        }
    }

    /** 删除刚上传的附件（输入卡片点 X）：台账置待删 + 入可靠队列异步删除，接口即时返回 */
    @org.springframework.web.bind.annotation.DeleteMapping("/bot/attachment")
    public BaseResponse<Boolean> delete(@RequestParam @NotBlank String path,
                                        jakarta.servlet.http.HttpServletRequest request) {
        if (!fileTransfer.available()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱未启用");
        }
        String trimmed = path.trim();
        if (!com.mio.ai.bot.model.dto.AttachmentItem.isStagedPath(trimmed)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        }
        Long userId = resolveUserId(request);
        requirePathOwnership(trimmed, userId);
        try {
            // 台账先置待删：入队失败也留下了持久凭证，兜底扫描会重入队
            if (!chatFileService.markPendingByPath(trimmed, userId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该附件");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("附件台账置待删失败, path={}: {}", trimmed, e.getMessage());
        }
        try {
            deleteQueue.enqueue(trimmed);
        } catch (Exception e) {
            // 入队失败不阻断前端：待删台账行由兜底扫描重入队
            log.warn("附件删除入队失败, path={}: {}", trimmed, e.getMessage());
        }
        return ResultUtils.success(true);
    }

    /**
     * 下载暂存文件（uploads/outputs 内）：浏览器以附件形式保存。
     * <p>只认请求头 token：不接受查询参数——token 出现在 URL 里会随 nginx 访问日志、
     * 浏览器历史与 Referer 外泄。图片缩略图由前端带请求头拉 blob 后渲染。
     */
    @GetMapping("/bot/attachment/download")
    public ResponseEntity<byte[]> download(@RequestParam @NotBlank String path,
                                           jakarta.servlet.http.HttpServletRequest request) {
        if (!fileTransfer.available()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱未启用，暂不支持附件下载");
        }
        if (!com.mio.ai.bot.model.dto.AttachmentItem.isStagedPath(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        }
        requirePathOwnership(path.trim(), resolveUserId(request));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            fileTransfer.downloadTo(path.trim(), out);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或已被清理");
        }
        String storedName = path.substring(path.lastIndexOf('/') + 1);
        // 去掉上传时的随机前缀，用户拿到的文件名保持原样
        String fileName = storedName.matches("[0-9a-f]{1,16}_.+") ? storedName.substring(storedName.indexOf('_') + 1) : storedName;
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(out.toByteArray());
    }

    /** 尽力解析上传者（游客可上传，未登录返回 null）：只认请求头 token */
    private Long resolveUserId(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String token = request == null ? null : request.getHeader("token");
            return StrUtil.isBlank(token) ? null : redisComponent.getUserIdNullable(token);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 会话归属校验（上传入口）：会话不存在视为新建放行；已存在时必须归当前用户。
     * 与 /bot/chat 同一口径——否则可向他人会话目录写入文件。
     */
    private void requireChatOwnership(String chatId, Long userId) {
        com.mio.ai.bot.model.entity.ChatConversationDO conversation =
                chatHistoryRepository.getChatByConversationId(chatId);
        if (conversation == null) {
            return;
        }
        if (userId == null || conversation.getUserId() == null
                || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该会话");
        }
    }

    /**
     * 暂存路径归属校验（下载/删除入口）：路径第二段即 chatId。
     * <p>会话行存在时按会话归属判定；会话行不存在（游客会话不落库、或上传早于首条消息）
     * 时回退到 chat_file 台账——台账记录了实际上传者，否则这类文件会变成"谁都能下"。
     */
    private void requirePathOwnership(String path, Long userId) {
        String chatId = com.mio.ai.bot.model.dto.AttachmentItem.chatIdOfStagedPath(path);
        if (chatId != null) {
            requireChatOwnership(chatId, userId);
        }
        // 会话行不存在时 requireChatOwnership 会直接放行，这里用台账做第二道判定
        Long ownerId = chatFileService.findOwnerByPath(path);
        if (ownerId != null && !ownerId.equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该文件");
        }
    }
}
