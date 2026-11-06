package com.mio.ai.bot.service.resource;

import com.mio.ai.bot.model.dto.AttachmentItem;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseCreateService;
import com.mio.ai.resource.service.knowledge.VectorizeTaskState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话内知识库文档摄取：把本轮对话附件（沙箱里的 uploads/ 文件）灌进知识库并后台向量化。
 * <p>为什么能脱离 SSE：{@code vectorizeFiles} 把任务 submit 到独立线程池后立即返回，
 * 进度写进 {@link VectorizeTaskState} 快照——SSE 只是推送通道，连接断了任务照跑。
 * 这里传一个"没人消费"的 emitter 启动任务，之后由前端轮询进度接口读快照。
 * <p>为什么不让前端直接传：浏览器上传是用户点选文件的交互，对话里模型拿不到文件句柄；
 * 而附件已经在沙箱里了，直接读比让用户再选一次更顺。
 */
@Slf4j
@Service
public class KnowledgeIngestService {

    /** 与 KnowledgeBaseCreateServiceImpl 的上传限制对齐（单文件 50MB） */
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    /** 单次上传文件数上限（与 controller 一致） */
    private static final int MAX_FILES = 20;
    /** 知识库支持的文件类型（与 KnowledgeBaseModal 的 accept 一致） */
    private static final List<String> ALLOWED_EXT = List.of("pdf", "doc", "docx", "md", "txt");

    private final KnowledgeBaseCreateService knowledgeBaseCreateService;
    private final VectorizeTaskState taskState;
    private final SandboxFileTransfer fileTransfer;

    public KnowledgeIngestService(KnowledgeBaseCreateService knowledgeBaseCreateService,
                                  VectorizeTaskState taskState,
                                  SandboxFileTransfer fileTransfer) {
        this.knowledgeBaseCreateService = knowledgeBaseCreateService;
        this.taskState = taskState;
        this.fileTransfer = fileTransfer;
    }

    /**
     * 本轮可摄取结果：接受的文件、跳过的文件（含原因）、跳过原因汇总。
     */
    public record IngestResult(int accepted, int rejected, List<String> rejectedReasons,
                               List<String> docNames, boolean vectorizeStarted) {
    }

    /**
     * 从沙箱读附件 → 上传进知识库 → 启动后台向量化。
     * <p>附件路径必须是本会话的暂存路径（uploads/ 根内），否则拒绝——防读别人会话的文件。
     */
    public IngestResult ingest(Long kbId, Long userId, String chatId, List<AttachmentItem> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new IngestResult(0, 0, List.of(), List.of(), false);
        }
        List<MultipartFile> files = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (AttachmentItem item : attachments) {
            if (files.size() >= MAX_FILES) {
                rejected.add(item.name() + "：超出单次 " + MAX_FILES + " 个文件上限");
                continue;
            }
            String reason = rejectReason(item, chatId);
            if (reason != null) {
                rejected.add(item.name() + "：" + reason);
                continue;
            }
            try {
                files.add(readAsMultipart(item));
                names.add(item.name());
            } catch (Exception e) {
                log.warn("读取沙箱附件失败 kbId={} path={}: {}", kbId, item.path(), e.getMessage());
                rejected.add(item.name() + "：读取失败");
            }
        }
        if (files.isEmpty()) {
            return new IngestResult(0, rejected.size(), rejected, List.of(), false);
        }

        List<Map<String, Object>> uploaded;
        try {
            uploaded = knowledgeBaseCreateService.uploadFiles(kbId, userId,
                    files.toArray(new MultipartFile[0]));
        } catch (Exception e) {
            log.warn("知识库上传失败 kbId={}: {}", kbId, e.getMessage());
            rejected.add("上传失败：" + e.getMessage());
            return new IngestResult(0, rejected.size(), rejected, List.of(), false);
        }
        int ok = 0;
        for (Map<String, Object> row : uploaded) {
            boolean success = "success".equals(String.valueOf(row.get("status")));
            if (success) {
                ok++;
            } else {
                rejected.add(String.valueOf(row.get("fileName")) + "："
                        + (row.get("error") == null ? "上传失败" : row.get("error")));
            }
        }
        if (ok == 0) {
            return new IngestResult(0, rejected.size(), rejected, List.of(), false);
        }

        // 启动后台向量化：emitter 无人消费也不影响任务（进度走快照）
        boolean started = false;
        try {
            SseEmitter sink = new SseEmitter(60_000L);
            knowledgeBaseCreateService.vectorizeFiles(kbId, userId, sink, false);
            started = true;
        } catch (Exception e) {
            log.warn("启动向量化失败 kbId={}: {}", kbId, e.getMessage());
        }
        return new IngestResult(ok, rejected.size(), rejected, names, started);
    }

    /** 读快照进度；无任务返回 null */
    public VectorizeTaskState.VectorizeProgress progress(Long kbId) {
        return taskState.snapshot(kbId);
    }

    public boolean isVectorizing(Long kbId) {
        return taskState.isRunning(kbId);
    }

    /** 附件校验：归属本会话 + 类型白名单 + 大小上限 */
    private String rejectReason(AttachmentItem item, String chatId) {
        if (!AttachmentItem.isStagedPath(item.path())) {
            return "路径不在会话暂存区";
        }
        String owner = AttachmentItem.chatIdOfStagedPath(item.path());
        if (owner == null || !owner.equals(chatId)) {
            return "不属于当前会话";
        }
        String ext = extOf(item.name() == null ? item.path() : item.name());
        if (!ALLOWED_EXT.contains(ext)) {
            return "不支持的类型 ." + ext + "（支持 " + String.join("/", ALLOWED_EXT) + "）";
        }
        if (item.size() > MAX_FILE_BYTES) {
            return "超过 50MB";
        }
        return null;
    }

    private MultipartFile readAsMultipart(AttachmentItem item) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fileTransfer.downloadTo(item.path(), out);
        String name = item.name() == null || item.name().isBlank()
                ? item.path().substring(item.path().lastIndexOf('/') + 1)
                : item.name();
        return new ByteArrayMultipartFile("files", name, contentTypeOf(name), out.toByteArray());
    }

    /**
     * 内存版 MultipartFile：沙箱文件已在服务端，不需要框架的 multipart 解析。
     * （spring-test 的 MockMultipartFile 只在 test scope，主代码不可用。）
     */
    private record ByteArrayMultipartFile(String fieldName, String originalFilename,
                                          String contentType, byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return fieldName;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private static String contentTypeOf(String name) {
        return switch (extOf(name)) {
            case "pdf" -> "application/pdf";
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }
}
