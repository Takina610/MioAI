package com.mio.ai.bot.controller;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.common.common.BaseResponse;
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

    /** 上传附件：返回 {path, name, size}，发送消息时随 attachments 参数带给 /bot/chat */
    @PostMapping("/bot/attachment")
    public BaseResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("chatId") @NotBlank @Size(max = 64) String chatId) {
        if (!fileTransfer.available()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱未启用，暂不支持上传附件");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单个文件不能超过 100MB");
        }
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "file");
        try {
            String path = fileTransfer.upload(chatId, originalName, file.getInputStream());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path);
            data.put("name", originalName);
            data.put("size", file.getSize());
            return ResultUtils.success(data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("附件上传失败, chatId={}, name={}: {}", chatId, originalName, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "附件上传失败，请重试");
        }
    }

    /** 删除刚上传的附件（输入卡片点 X）：入可靠队列异步删除，接口即时返回 */
    @org.springframework.web.bind.annotation.DeleteMapping("/bot/attachment")
    public BaseResponse<Boolean> delete(@RequestParam @NotBlank String path) {
        if (!fileTransfer.available()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱未启用");
        }
        if (!com.mio.ai.bot.model.dto.AttachmentItem.isStagedPath(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        }
        try {
            deleteQueue.enqueue(path.trim());
        } catch (Exception e) {
            // 入队失败不阻断前端：文件由 VPS 清理 cron 兜底删除
            log.warn("附件删除入队失败, path={}: {}", path, e.getMessage());
        }
        return ResultUtils.success(true);
    }

    /** 下载暂存文件（uploads/outputs 内）：浏览器以附件形式保存 */
    @GetMapping("/bot/attachment/download")
    public ResponseEntity<byte[]> download(@RequestParam @NotBlank String path) {
        if (!fileTransfer.available()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "沙箱未启用，暂不支持附件下载");
        }
        if (!com.mio.ai.bot.model.dto.AttachmentItem.isStagedPath(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        }
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
}
