package com.mio.ai.customagent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.common.common.FileType;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.customagent.mapper.DocumentMapper;
import com.mio.ai.customagent.model.entity.Document;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.rag.MarkdownReader;
import com.mio.ai.customagent.rag.PdfReader;
import com.mio.ai.customagent.rag.TikaReader;
import com.mio.ai.customagent.rag.TxtReader;
import com.mio.ai.customagent.service.KnowledgeBaseCreateService;
import com.mio.ai.customagent.service.KnowledgeBaseService;
import com.mio.ai.superagent.rag.CustomTokenTextSplitter;
import com.mio.ai.superagent.rag.KeywordEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Autowired
    MarkdownReader markdownReader;
    @Autowired
    PdfReader pdfReader;
    @Autowired
    TxtReader txtReader;
    @Autowired
    TikaReader tikaReader;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

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
                List<Document> documents = documentMapper.selectList(
                    new QueryWrapper<Document>()
                        .eq("kb_id", kbId)
                        .eq("status", 0)
                );

                int total = documents.size();
                int completed = 0;

                emitter.send(SseEmitter.event()
                        .data(buildProgressEvent(total, 0, "开始处理文件...")));

                for (Document doc : documents) {
                    try {
                        doc.setStatus(1);
                        documentMapper.updateById(doc);

                        emitter.send(SseEmitter.event()
                                .data(buildProgressEvent(total, completed,
                            "正在处理: " + doc.getFileName(), doc.getFileName())));

                        List<org.springframework.ai.document.Document> aiDocuments =
                                readDocumentFromUrl(doc.getFilePath(), doc.getFileType());
                        
                        if (aiDocuments.isEmpty()) {
                            doc.setStatus(3);
                            documentMapper.updateById(doc);
                            completed++;
                            continue;
                        }

                        CustomTokenTextSplitter splitter = new CustomTokenTextSplitter();
                        List<org.springframework.ai.document.Document> splitDocuments =
                                splitter.apply(aiDocuments);

                        int chunkIndex = 0;
                        List<org.springframework.ai.document.Document> documentsWithId = new java.util.ArrayList<>();
                        for (org.springframework.ai.document.Document aiDoc : splitDocuments) {
                            Map<String, Object> metadata = new java.util.HashMap<>(aiDoc.getMetadata());
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

                        List<org.springframework.ai.document.Document> enrichedDocuments =
                                keywordEnricher.enrich(documentsWithId);

                        vectorStore.add(enrichedDocuments);

                        doc.setStatus(2);
                        documentMapper.updateById(doc);
                        completed++;

                        emitter.send(SseEmitter.event().data(buildProgressEvent(total, completed, 
                            "已完成: " + doc.getFileName())));

                    } catch (Exception e) {
                        log.error("处理文件失败: {}", doc.getFileName(), e);
                        doc.setStatus(3);
                        documentMapper.updateById(doc);
                        completed++;
                        emitter.send(SseEmitter.event().data(buildProgressEvent(total, completed, 
                            "处理失败: " + doc.getFileName())));
                    }
                }

                emitter.send(SseEmitter.event().data(buildDoneEvent(total, completed)));
                
                updateKnowledgeBaseStats(kbId);
                
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
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            return;
        }

        List<Document> documents = documentMapper.selectList(
            new QueryWrapper<Document>()
                .eq("kb_id", kbId)
        );

        for (Document doc : documents) {
            try {
                if (doc.getFilePath() != null && !doc.getFilePath().isEmpty()) {
                    r2Util.deleteFile(doc.getFilePath());
                }
                documentMapper.deleteById(doc.getId());
            } catch (Exception e) {
                log.error("删除文件失败: {}", doc.getFileName(), e);
            }
        }

        knowledgeBaseService.removeById(kbId);
    }

    private List<org.springframework.ai.document.Document> readDocumentFromUrl(String fileUrl,
                                                                               String fileType
    ) throws IOException {
        File tempFile = downloadFileFromUrl(fileUrl);
        try {
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

    private File downloadFileFromUrl(String fileUrl) throws IOException {
        String encodedUrl = encodeUrl(fileUrl);
        URL url = new URL(encodedUrl);
        File tempFile = File.createTempFile("kb_doc_", ".tmp");
        
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        return tempFile;
    }

    private String encodeUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();
            String host = parsedUrl.getHost();
            String path = parsedUrl.getPath();
            String query = parsedUrl.getQuery();
            String ref = parsedUrl.getRef();

            String encodedPath = encodePath(path);

            StringBuilder result = new StringBuilder();
            result.append(protocol).append("://").append(host).append(encodedPath);

            if (query != null) {
                result.append("?").append(query);
            }
            if (ref != null) {
                result.append("#").append(ref);
            }

            return result.toString();
        } catch (Exception e) {
            log.warn("URL编码失败，使用原始URL: {}", url, e);
            return url;
        }
    }

    private String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        StringBuilder encoded = new StringBuilder();
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }
            String segment = segments[i];
            if (!segment.isEmpty()) {
                encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        return encoded.toString();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
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

    private String buildDoneEvent(int totalFiles, int completedFiles) {
        Map<String, Object> data = new HashMap<>();
        data.put("totalFiles", totalFiles);
        data.put("completedFiles", completedFiles);
        data.put("message", "向量化完成");
        Map<String, Object> event = new HashMap<>();
        event.put("type", "done");
        event.put("data", data);
        return JacksonUtil.writeValueAsString(event);
    }

    private void updateKnowledgeBaseStats(Long kbId) {
        List<Document> successDocuments = documentMapper.selectList(
            new QueryWrapper<Document>()
                .eq("kb_id", kbId)
                .eq("status", 2)
        );

        int documentCount = successDocuments.size();
        long totalSize = successDocuments.stream()
                .mapToLong(Document::getFileSize)
                .sum();

        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb != null) {
            kb.setDocumentCount(documentCount);
            kb.setStorageSize(totalSize);
            knowledgeBaseService.updateById(kb);
        }
    }
}
