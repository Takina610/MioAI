package com.mio.ai.resource.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.resource.mapper.knowledge.DocumentMapper;
import com.mio.ai.resource.model.entity.Document;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.enums.DocumentStatusEnum;
import com.mio.ai.common.utils.R2Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 文档关联资源清理（向量数据 / R2 文件 / 记录 / 知识库统计）。
 * <p>向量清理直接按 metadata.docId 过滤删除（vector_store 的 metadata 在写入时固定携带 docId），
 * 不依赖任何外部索引；外部资源先清理，最后删记录，避免孤立数据。
 */
@Slf4j
@Service
public class DocumentCleanupService {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private R2Util r2Util;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    @Lazy
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 删除单个文档的全部向量分块
     */
    public void deleteVectorsByDocId(Long docId) {
        if (docId == null) {
            return;
        }
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("docId", docId).build());
            log.info("删除向量数据成功: docId={}", docId);
        } catch (Exception e) {
            log.warn("删除向量数据失败: docId={}, {}", docId, e.getMessage());
        }
    }

    /**
     * 删除单个文档的全部关联数据（向量 -> R2 文件 -> 数据库记录）
     */
    public void deleteDocumentAssets(Document doc) {
        deleteVectorsByDocId(doc.getId());
        try {
            if (doc.getFilePath() != null && !doc.getFilePath().isEmpty()) {
                r2Util.deleteFile(doc.getFilePath());
            }
        } catch (Exception e) {
            log.error("删除R2文件失败: {}", doc.getFileName(), e);
        }
        try {
            documentMapper.deleteById(doc.getId());
            log.info("删除文档记录成功: docId={}", doc.getId());
        } catch (Exception e) {
            log.error("删除数据库记录失败: docId={}", doc.getId(), e);
        }
    }

    /**
     * 按已完成文档刷新知识库的文档数与存储量统计
     */
    public void updateKnowledgeBaseStats(Long kbId) {
        List<Document> successDocuments = documentMapper.selectList(
                new QueryWrapper<Document>()
                        .eq("kb_id", kbId)
                        .eq("status", DocumentStatusEnum.COMPLETED.getCode()));

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
