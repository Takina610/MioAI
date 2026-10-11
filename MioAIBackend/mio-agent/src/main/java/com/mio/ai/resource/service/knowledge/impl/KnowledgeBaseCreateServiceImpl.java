package com.mio.ai.resource.service.knowledge.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.common.common.FileType;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.resource.mapper.knowledge.DocumentMapper;
import com.mio.ai.resource.model.entity.Document;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.framework.rag.MarkdownReader;
import com.mio.ai.framework.rag.PdfReader;
import com.mio.ai.framework.rag.TikaReader;
import com.mio.ai.framework.rag.TxtReader;
import com.mio.ai.resource.service.knowledge.DocumentCleanupService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseCreateService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.framework.rag.CustomTokenTextSplitter;
import com.mio.ai.framework.rag.KeywordEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author: Takina
 * @date: 2026/4/5
 * @description: 知识库创建服务实现
 * <p>向量化任务模型：任务由第一个 SSE 连接触发，之后只跑一份（runningKbIds 防重入）；
 * 实时进度写入 progressMap 快照，任何时刻新开的 SSE 连接（刷新页面/网络波动重连/换设备）
 * 都以观察者身份从快照接管进度，与 Agent 会话的重连回放同构。服务重启后快照丢失，
 * 此时重连会走正常启动路径并幂等处理（只处理 status 0/1/3 的文档）。
 */
@Slf4j
@Service
public class KnowledgeBaseCreateServiceImpl implements KnowledgeBaseCreateService {

    /**
     * 单文件大小上限（与前端一致）
     */
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    /**
     * 单次上传文件数上限
     */
    private static final int MAX_FILES_PER_UPLOAD = 20;

    @Autowired
    private R2Util r2Util;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private KeywordEnricher keywordEnricher;

    /**
     * 分块大小（token），可通过 mio.ai.rag.chunk-size 调整，用于 RAG 分块消融实验（400/800/1200）
     */
    @Value("${mio.ai.rag.chunk-size:800}")
    private int chunkSize;

    /**
     * 入库前用对话模型为分块提取关键词写入 metadata（当前检索不消费该字段），默认关闭
     */
    @Value("${mio.ai.rag.keyword-enrich:false}")
    private boolean keywordEnrichEnabled;

    /**
     * 嵌入请求全局节流（与查询嵌入共享 RPM 限额）
     */
    @Autowired
    private com.mio.ai.framework.rag.EmbeddingThrottle embeddingThrottle;

    @Autowired
    private DocumentCleanupService documentCleanupService;

    @Autowired
    MarkdownReader markdownReader;
    @Autowired
    PdfReader pdfReader;
    @Autowired
    TxtReader txtReader;
    @Autowired
    TikaReader tikaReader;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // 存储正在进行的向量化任务的中断标志
    private final Map<Long, Boolean> cancellationFlags = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 正在执行向量化任务的知识库集合：同一 KB 的任务全局只跑一份，
     * 后续 SSE 连接一律进入观察者模式
     */
    private final Set<Long> runningKbIds = ConcurrentHashMap.newKeySet();

    /**
     * 向量化进度快照（KB 维度）：任务线程写，所有 SSE 连接（发起者 + 观察者）读
     */
    private final Map<Long, VectorizeProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * 向量化进度快照。done 为终态标志；failed/cancelled 细分终态类型
     */
    public static class VectorizeProgress {
        volatile int total;
        final AtomicInteger current = new AtomicInteger();
        final AtomicInteger failedCount = new AtomicInteger();
        volatile String message = "";
        volatile String fileName;
        volatile boolean done;
        volatile boolean failed;
        volatile boolean cancelled;
        volatile String errorMessage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> uploadFiles(Long kbId, Long userId, MultipartFile[] files) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择要上传的文件");
        }
        if (files.length > MAX_FILES_PER_UPLOAD) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单次最多上传 " + MAX_FILES_PER_UPLOAD + " 个文件");
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> result = new HashMap<>();
            String originalFilename = file.getOriginalFilename();
            result.put("fileName", originalFilename);
            result.put("fileSize", file.getSize());

            try {
                if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "单个文件不能超过 50MB");
                }
                String fileUrl = r2Util.uploadFile(file, FileType.KNOWLEDGE_FILE, String.valueOf(kbId));

                Document document = new Document();
                document.setKbId(kbId);
                document.setFileName(originalFilename);
                document.setFileType(getFileExtension(originalFilename));
                document.setFileSize(file.getSize());
                document.setFilePath(fileUrl);
                document.setStatus(0);
                documentMapper.insert(document);

                result.put("docId", document.getId());
                result.put("fileUrl", fileUrl);
                result.put("status", "success");
            } catch (Exception e) {
                log.error("上传文件失败: {}", originalFilename, e);
                result.put("status", "error");
                result.put("error", e.getMessage());
            }

            results.add(result);
        }

        return results;
    }

    @Override
    public void vectorizeFiles(Long kbId, Long userId, SseEmitter emitter) {
        vectorizeFiles(kbId, userId, emitter, false);
    }

    @Override
    public void vectorizeFiles(Long kbId, Long userId, SseEmitter emitter, boolean force) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            sendRawEvent(emitter, "{\"type\":\"error\",\"data\":{\"message\":\"知识库不存在\"}}");
            return;
        }

        // 防重入：任务已在跑时本次连接只做观察者（无论 force——全量重建也不允许与进行中的任务并发）
        if (!runningKbIds.add(kbId)) {
            log.info("向量化任务进行中，SSE 连接转为观察者模式: kbId={}", kbId);
            observeProgress(kbId, emitter);
            return;
        }

        // 全量重建：所有文档（含已完成）重置为待处理，按知识库当前分块配置重新向量化
        if (force) {
            documentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Document>()
                    .eq("kb_id", kbId).eq("is_deleted", 0).set("status", 0));
            log.info("全量重建向量化: kbId={}, chunkSize={}, chunkOverlap={}",
                    kbId, kb.getChunkSize(), kb.getChunkOverlap());
        }

        executorService.submit(() -> runVectorizeTask(kbId, kb, emitter));
    }

    /**
     * 向量化主任务：进度同时推给发起 SSE 并写快照，供观察者连接与页面刷新后恢复
     */
    private void runVectorizeTask(Long kbId, KnowledgeBase kb, SseEmitter emitter) {
        VectorizeProgress progress = new VectorizeProgress();
        progressMap.put(kbId, progress);
        try {
            // 清除中断标志
            cancellationFlags.remove(kbId);

            List<Document> documents = documentMapper.selectList(
                new QueryWrapper<Document>()
                    .eq("kb_id", kbId)
                    // 0=待处理，1=上次中断残留，3=上次失败：三者都允许（重新）向量化
                    .in("status", 0, 1, 3)
            );

            int total = documents.size();
            progress.total = total;
            if (total == 0) {
                sendTerminalDone(progress, emitter, 0, 0, 0);
                return;
            }

            // 分批次处理，每批处理2个文件
            int batchSize = 2;
            int batchCount = (int) Math.ceil((double) total / batchSize);

            List<List<Document>> batches = IntStream.range(0, batchCount)
                    .mapToObj(i -> new ArrayList<>(documents.subList(
                            i * batchSize,
                            Math.min((i + 1) * batchSize, total))))
                    .collect(Collectors.toList());

            log.info("共 {} 个文件，分为 {} 批次并发处理", total, batchCount);

            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            CountDownLatch countDownLatch = new CountDownLatch(batchCount);

            sendProgress(progress, emitter, total, 0, "开始处理文件...", null);

            for (List<Document> batch : batches) {
                executorService.submit(() -> {
                    try {
                        for (Document doc : batch) {
                            // 检查是否被中断
                            if (Boolean.TRUE.equals(cancellationFlags.get(kbId))) {
                                log.info("向量化任务被中断: kbId={}", kbId);
                                return;
                            }

                            try {
                                // 重试场景：先清掉上次可能残留的部分向量，避免向量主键冲突
                                documentCleanupService.deleteVectorsByDocId(doc.getId());

                                doc.setStatus(1);
                                documentMapper.updateById(doc);

                                sendProgress(progress, emitter, total, completed.get(),
                                        "正在处理: " + doc.getFileName(), doc.getFileName());

                                List<org.springframework.ai.document.Document> aiDocuments =
                                        readDocumentFromUrl(doc.getFilePath(), doc.getFileType());

                                if (aiDocuments.isEmpty()) {
                                    doc.setStatus(3);
                                    documentMapper.updateById(doc);
                                    completed.incrementAndGet();
                                    continue;
                                }

                                // 分块配置以知识库级设置优先，未设置时回退全局默认
                                CustomTokenTextSplitter.Builder splitterBuilder = CustomTokenTextSplitter.builder()
                                        .withChunkSize(kb.getChunkSize() != null ? kb.getChunkSize() : chunkSize);
                                if (kb.getChunkOverlap() != null) {
                                    splitterBuilder.withChunkOverlap(kb.getChunkOverlap());
                                }
                                List<org.springframework.ai.document.Document> splitDocuments =
                                        splitterBuilder.build().apply(aiDocuments);

                                int chunkIndex = 0;
                                List<org.springframework.ai.document.Document> documentsWithId = new ArrayList<>();
                                for (org.springframework.ai.document.Document aiDoc : splitDocuments) {
                                    Map<String, Object> metadata = new HashMap<>(aiDoc.getMetadata());
                                    // kbId/docId 必须存数值：检索与清理的过滤条件走
                                    // metadata::jsonb @@ '$.kbId == 5' 的 jsonpath 数值比较，字符串值永远匹配不上
                                    metadata.put("kbId", kbId);
                                    metadata.put("docId", doc.getId());
                                    metadata.put("fileName", doc.getFileName());
                                    metadata.put("chunkIndex", chunkIndex);

                                    String docId = "doc_" + doc.getId() + "_" + chunkIndex;
                                    org.springframework.ai.document.Document docWithId =
                                            new org.springframework.ai.document.Document(docId, aiDoc.getText(), metadata);
                                    documentsWithId.add(docWithId);
                                    chunkIndex++;
                                }

                                List<org.springframework.ai.document.Document> enrichedDocuments = documentsWithId;
                                if (keywordEnrichEnabled) {
                                    try {
                                        enrichedDocuments = keywordEnricher.enrich(documentsWithId);
                                    } catch (Exception e) {
                                        log.warn("关键词增强失败，使用原始分块继续向量化: {}", e.getMessage());
                                    }
                                }

                                // 分批添加到向量存储，每批最多5个文档
                                int embeddingBatchSize = 5;
                                for (int i = 0; i < enrichedDocuments.size(); i += embeddingBatchSize) {
                                    // 检查是否被中断
                                    if (Boolean.TRUE.equals(cancellationFlags.get(kbId))) {
                                        log.info("向量化任务被中断: kbId={}", kbId);
                                        return;
                                    }

                                    int end = Math.min(i + embeddingBatchSize, enrichedDocuments.size());
                                    List<org.springframework.ai.document.Document> embeddingBatch =
                                            enrichedDocuments.subList(i, end);
                                    addEmbeddingBatchWithRetry(embeddingBatch);
                                    log.info("向量化批次完成: {}/{}", Math.min(i + embeddingBatchSize, enrichedDocuments.size()), enrichedDocuments.size());
                                }

                                doc.setStatus(2);
                                documentMapper.updateById(doc);
                                int currentCompleted = completed.incrementAndGet();

                                sendProgress(progress, emitter, total, currentCompleted,
                                        "已完成: " + doc.getFileName(), null);

                            } catch (Exception e) {
                                log.error("处理文件失败: {}", doc.getFileName(), e);
                                doc.setStatus(3);
                                documentMapper.updateById(doc);
                                int currentCompleted = completed.incrementAndGet();
                                failedCount.incrementAndGet();
                                sendProgress(progress, emitter, total, currentCompleted,
                                        "处理失败: " + doc.getFileName() + " - " + e.getMessage(), null);
                            }
                        }
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            // 等待所有批次完成
            countDownLatch.await(30, java.util.concurrent.TimeUnit.MINUTES);

            // 检查是否被中断
            if (Boolean.TRUE.equals(cancellationFlags.get(kbId))) {
                log.info("向量化任务已取消: kbId={}", kbId);
                progress.cancelled = true;
                progress.done = true;
                sendRawEvent(emitter, "{\"type\":\"cancelled\",\"data\":{\"message\":\"向量化已取消\"}}");
                return;
            }

            // 检查是否所有文件都失败了：保留文件与记录，修复问题后重新向量化即可重试
            int finalFailedCount = failedCount.get();
            progress.failedCount.set(finalFailedCount);
            if (finalFailedCount == total) {
                log.warn("所有文件处理失败，已保留待重试: kbId={}", kbId);
                progress.failed = true;
                progress.errorMessage = "全部 " + total + " 个文件处理失败，文件已保留，可重新向量化重试";
                progress.done = true;
                sendRawEvent(emitter, "{\"type\":\"error\",\"data\":{\"message\":\"" + progress.errorMessage + "\"}}");
                return;
            }

            sendTerminalDone(progress, emitter, total, completed.get(), finalFailedCount);

            documentCleanupService.updateKnowledgeBaseStats(kbId);

        } catch (Exception e) {
            log.error("向量化处理失败", e);
            progress.failed = true;
            progress.errorMessage = e.getMessage() != null ? e.getMessage() : "向量化处理失败";
            progress.done = true;
            sendRawEvent(emitter, "{\"type\":\"error\",\"data\":{\"message\":\"" + progress.errorMessage + "\"}}");
        } finally {
            progress.done = true;
            runningKbIds.remove(kbId);
        }
    }

    /**
     * 观察者模式：任务进行中时新开的 SSE 连接不重启任务，按秒轮询进度快照直到终态。
     * 服务重启导致快照丢失时直接发 done（前端会拉取到真实的文档状态）
     */
    private void observeProgress(Long kbId, SseEmitter emitter) {
        VectorizeProgress progress = progressMap.get(kbId);
        if (progress == null) {
            // 快照不存在（服务重启/历史任务）：发 done 让前端刷新文档状态即可
            sendRawEvent(emitter, buildDoneEvent(0, 0, 0));
            return;
        }
        sendProgress(progress, emitter, progress.total, progress.current.get(),
                progress.message, progress.fileName);
        executorService.submit(() -> {
            try {
                while (!progress.done) {
                    Thread.sleep(1000);
                    sendProgress(progress, emitter, progress.total, progress.current.get(),
                            progress.message, progress.fileName);
                }
                sendTerminalFromSnapshot(emitter, progress);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 观察者客户端断开：静默退出，不影响任务
                log.debug("观察者 SSE 连接断开: kbId={}", kbId);
            } finally {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void sendTerminalFromSnapshot(SseEmitter emitter, VectorizeProgress p) throws IOException {
        if (p.cancelled) {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .data("{\"type\":\"cancelled\",\"data\":{\"message\":\"向量化已取消\"}}"));
            }
        } else if (p.failed) {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(buildErrorEvent(p.errorMessage)));
            }
        } else {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(buildDoneEvent(p.total,
                        p.current.get(), p.failedCount.get())));
            }
        }
    }

    private void sendTerminalDone(VectorizeProgress progress, SseEmitter emitter,
                                  int total, int completed, int failedFiles) {
        progress.done = true;
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(buildDoneEvent(total, completed, failedFiles)));
            }
            emitter.complete();
        } catch (Exception e) {
            log.debug("任务主 SSE 已断开，完成态由快照供观察者消费: {}", e.getMessage());
        }
    }

    /**
     * 进度推送 + 快照同步。任务主连接尽力而为：客户端断开（刷新/网络波动）只丢推送，
     * 绝不影响任务本身的成败判定——真实状态以快照与 DB 为准，重连的观察者从快照接管
     */
    private void sendProgress(VectorizeProgress progress, SseEmitter emitter,
                              int total, int current, String message, String fileName) {
        if (progress != null) {
            progress.total = total;
            progress.message = message != null ? message : "";
            progress.fileName = fileName;
            progress.current.set(current);
        }
        if (emitter == null) {
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .data(buildProgressEvent(total, current, message, fileName)));
            }
        } catch (Exception e) {
            log.debug("任务主 SSE 连接已断开，进度由快照供观察者消费: {}", e.getMessage());
        }
    }

    private void sendRawEvent(SseEmitter emitter, String json) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().data(json));
            }
            emitter.complete();
        } catch (IOException e) {
            log.error("发送SSE消息失败", e);
        } catch (IllegalStateException ignored) {
            // 连接已关闭/完成，响应不可写
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCreation(Long kbId, Long userId) {
        // 设置中断标志
        cancellationFlags.put(kbId, true);
        log.info("设置向量化中断标志: kbId={}", kbId);

        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            return;
        }

        // 删除知识库及其所有数据
        knowledgeBaseService.deleteKnowledgeBaseCascade(kbId);

        // 清除中断标志与进度快照
        cancellationFlags.remove(kbId);
        progressMap.remove(kbId);
    }

    @Override
    public List<Map<String, Object>> listActiveVectorizations(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<KnowledgeBase> ownKbs = knowledgeBaseService.lambdaQuery()
                .eq(KnowledgeBase::getUserId, userId)
                .list();
        if (ownKbs.isEmpty()) {
            return List.of();
        }
        List<Long> kbIds = ownKbs.stream().map(KnowledgeBase::getId).toList();
        Map<Long, KnowledgeBase> kbMap = ownKbs.stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, k -> k, (a, b) -> a));

        // 只统计真正在途的文档（0=待处理 1=处理中）；失败(3)是终态，不触发自动恢复
        List<Map<String, Object>> pendingRows = documentMapper.selectMaps(
                new QueryWrapper<Document>()
                        .select("kb_id", "count(*) AS pending_count")
                        .in("kb_id", kbIds)
                        .in("status", 0, 1)
                        .eq("is_deleted", 0)
                        .groupBy("kb_id"));

        return pendingRows.stream().map(row -> {
            Long rowKbId = Long.valueOf(String.valueOf(row.get("kb_id")));
            KnowledgeBase kb = kbMap.get(rowKbId);
            Map<String, Object> item = new HashMap<>();
            item.put("kbId", rowKbId);
            item.put("kbName", kb != null ? kb.getName() : "");
            item.put("pendingDocs", Integer.parseInt(String.valueOf(row.getOrDefault("pending_count", "0"))));
            item.put("running", runningKbIds.contains(rowKbId));
            return item;
        }).toList();
    }

    private List<org.springframework.ai.document.Document> readDocumentFromUrl(String fileUrl,
                                                                               String fileType
    ) throws IOException {
        // 直接走 S3 API 取回对象：自托管 CDN 证书/网络抖动都会掐断整个向量化，
        // 且文件本来就在自己的 R2 桶里，没有理由绕道公网域名
        byte[] bytes = r2Util.downloadFile(fileUrl);
        File tempFile = File.createTempFile("kb_doc_", ".tmp");
        try {
            java.nio.file.Files.write(tempFile.toPath(), bytes);
            FileSystemResource resource = new FileSystemResource(tempFile);
            switch (fileType.toLowerCase()) {
                case "pdf":
                    return pdfReader.loadPdf(resource);
                case "md":
                case "markdown":
                    return markdownReader.loadMarkdown(resource);
                case "txt":
                    return txtReader.loadTxt(tempFile);
                default:
                    return tikaReader.loadTika(resource);
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 嵌入批次写入：触发网关限流（429）时按 15s/30s/60s 退避重试，其他异常快速重试一次；
     * 仍失败则抛出，让该文件走失败保留逻辑
     */
    private void addEmbeddingBatchWithRetry(List<org.springframework.ai.document.Document> batch)
            throws InterruptedException {
        int attempt = 0;
        long backoff = 15000L;
        while (true) {
            embeddingThrottle.awaitTurn();
            try {
                vectorStore.add(batch);
                return;
            } catch (Exception e) {
                boolean rateLimited = isRateLimitError(e);
                attempt++;
                if (rateLimited && attempt <= 3) {
                    log.warn("嵌入请求触发限流，{}s 后重试({}/3): {}", backoff / 1000, attempt, e.getMessage());
                    Thread.sleep(backoff);
                    backoff = Math.min(backoff * 2, 60000L);
                    continue;
                }
                if (!rateLimited && attempt == 1) {
                    log.warn("向量化批次失败，2s 后重试: {}", e.getMessage());
                    Thread.sleep(2000);
                    continue;
                }
                throw e;
            }
        }
    }

    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName().contains("RateLimit")
                || (message != null && (message.contains("429") || message.contains("请求数限制")));
    }

    private String buildProgressEvent(int total, int current, String message) {
        return buildProgressEvent(total, current, message, null);
    }

    private String buildProgressEvent(int total, int current, String message, String fileName) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("current", current);
        data.put("message", message);
        if (fileName != null) {
            data.put("fileName", fileName);
        }
        Map<String, Object> event = new HashMap<>();
        event.put("type", "progress");
        event.put("data", data);
        return JacksonUtil.writeValueAsString(event);
    }

    private String buildDoneEvent(int totalFiles, int completedFiles, int failedFiles) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalFiles", totalFiles);
        data.put("completedFiles", completedFiles);
        data.put("failedFiles", failedFiles);
        if (failedFiles > 0) {
            data.put("message", "向量化完成，" + failedFiles + " 个文件处理失败");
        } else {
            data.put("message", "向量化完成");
        }
        Map<String, Object> event = new HashMap<>();
        event.put("type", "done");
        event.put("data", data);
        return JacksonUtil.writeValueAsString(event);
    }

    private String buildErrorEvent(String message) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "error");
        Map<String, Object> data = new HashMap<>();
        data.put("message", message != null ? message : "处理失败");
        event.put("data", data);
        return JacksonUtil.writeValueAsString(event);
    }
}
