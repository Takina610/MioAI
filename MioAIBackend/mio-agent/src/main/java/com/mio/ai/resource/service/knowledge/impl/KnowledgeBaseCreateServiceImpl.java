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
 */
@Slf4j
@Service
public class KnowledgeBaseCreateServiceImpl implements KnowledgeBaseCreateService {

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> uploadFiles(Long kbId, Long userId, MultipartFile[] files) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> result = new HashMap<>();
            String originalFilename = file.getOriginalFilename();
            result.put("fileName", originalFilename);
            result.put("fileSize", file.getSize());

            try {
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
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            try {
                emitter.send(SseEmitter.event()
                        .data("{\"type\":\"error\",\"data\":{\"message\":\"知识库不存在\"}}"));
                emitter.complete();
            } catch (IOException e) {
                log.error("发送SSE消息失败", e);
            }
            return;
        }

        executorService.submit(() -> {
            try {
                // 清除中断标志
                cancellationFlags.remove(kbId);
                
                List<Document> documents = documentMapper.selectList(
                    new QueryWrapper<Document>()
                        .eq("kb_id", kbId)
                        .eq("status", 0)
                );

                int total = documents.size();
                if (total == 0) {
                    emitter.send(SseEmitter.event().data(buildDoneEvent(0, 0, 0)));
                    emitter.complete();
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

                emitter.send(SseEmitter.event()
                        .data(buildProgressEvent(total, 0, "开始处理文件...")));

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
                                    doc.setStatus(1);
                                    documentMapper.updateById(doc);

                                    synchronized (emitter) {
                                        emitter.send(SseEmitter.event()
                                                .data(buildProgressEvent(total, completed.get(),
                                                        "正在处理: " + doc.getFileName(), doc.getFileName())));
                                    }

                                    List<org.springframework.ai.document.Document> aiDocuments =
                                            readDocumentFromUrl(doc.getFilePath(), doc.getFileType());

                                    if (aiDocuments.isEmpty()) {
                                        doc.setStatus(3);
                                        documentMapper.updateById(doc);
                                        completed.incrementAndGet();
                                        continue;
                                    }

                                    CustomTokenTextSplitter splitter = CustomTokenTextSplitter.builder()
                                            .withChunkSize(chunkSize)
                                            .build();
                                    List<org.springframework.ai.document.Document> splitDocuments =
                                            splitter.apply(aiDocuments);

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
                                        vectorStore.add(embeddingBatch);
                                        log.info("向量化批次完成: {}/{}", Math.min(i + embeddingBatchSize, enrichedDocuments.size()), enrichedDocuments.size());
                                    }

                                    doc.setStatus(2);
                                    documentMapper.updateById(doc);
                                    int currentCompleted = completed.incrementAndGet();

                                    synchronized (emitter) {
                                        emitter.send(SseEmitter.event().data(buildProgressEvent(total, currentCompleted,
                                                "已完成: " + doc.getFileName())));
                                    }

                                } catch (Exception e) {
                                    log.error("处理文件失败: {}", doc.getFileName(), e);
                                    doc.setStatus(3);
                                    documentMapper.updateById(doc);
                                    int currentCompleted = completed.incrementAndGet();
                                    failedCount.incrementAndGet();
                                    synchronized (emitter) {
                                        try {
                                            emitter.send(SseEmitter.event().data(buildProgressEvent(total, currentCompleted,
                                                    "处理失败: " + doc.getFileName() + " - " + e.getMessage())));
                                        } catch (IOException ex) {
                                            throw new RuntimeException(ex);
                                        }
                                    }
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
                    emitter.send(SseEmitter.event()
                            .data("{\"type\":\"cancelled\",\"data\":{\"message\":\"向量化已取消\"}}"));
                    emitter.complete();
                    return;
                }
                
                // 检查是否所有文件都失败了
                int finalFailedCount = failedCount.get();
                if (finalFailedCount == total) {
                    log.warn("所有文件处理失败，自动清理: kbId={}", kbId);
                    // 清理所有数据
                    cleanupFailedKnowledgeBase(kbId);
                    emitter.send(SseEmitter.event()
                            .data("{\"type\":\"error\",\"data\":{\"message\":\"所有文件处理失败，已自动清理\"}}"));
                    emitter.complete();
                    return;
                }
                
                emitter.send(SseEmitter.event().data(buildDoneEvent(total, completed.get(), finalFailedCount)));

                documentCleanupService.updateKnowledgeBaseStats(kbId);

                emitter.complete();

            } catch (Exception e) {
                log.error("向量化处理失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .data("{\"type\":\"error\",\"data\":{\"message\":\"" +
                        e.getMessage() + "\"}}"));
                    emitter.complete();
                } catch (IOException ex) {
                    log.error("发送SSE消息失败", ex);
                }
            }
        });
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

        // 清除中断标志
        cancellationFlags.remove(kbId);
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
     * 清理失败的知识库数据
     */
    private void cleanupFailedKnowledgeBase(Long kbId) {
        knowledgeBaseService.deleteKnowledgeBaseCascade(kbId);
        log.info("清理失败知识库完成: kbId={}", kbId);
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
}
