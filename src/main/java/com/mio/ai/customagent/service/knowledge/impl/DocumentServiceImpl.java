package com.mio.ai.customagent.service.knowledge.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.customagent.mapper.knowledge.DocumentMapper;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.entity.Document;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.model.enums.DocumentStatusEnum;
import com.mio.ai.customagent.model.vo.knowledge.DocumentVO;
import com.mio.ai.customagent.service.knowledge.DocumentService;
import com.mio.ai.customagent.service.knowledge.KnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Resource
    @Lazy
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private R2Util r2Util;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long addDocument(DocumentAddRequest request, Long userId) {
        if (request.getKbId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        if (StringUtils.isBlank(request.getFileName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(request.getKbId());
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        Document document = new Document();
        BeanUtil.copyProperties(request, document);
        document.setStatus(DocumentStatusEnum.PENDING.getCode());
        this.save(document);
        return document.getId();
    }

    @Override
    public boolean deleteDocument(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档ID不能为空");
        }
        Document document = this.getById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(document.getKbId());
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }

        if (document.getFilePath() != null && !document.getFilePath().isEmpty()) {
            try {
                r2Util.deleteFile(document.getFilePath());
            } catch (Exception e) {
                log.warn("删除R2文件失败: {}", document.getFilePath(), e);
            }
        }

        if (document.getStatus() == DocumentStatusEnum.COMPLETED.getCode()) {
            try {
                List<String> idsToDelete = new ArrayList<>();
                String pattern = "rag:doc_" + id + "_*";
                
                Set<String> keys = stringRedisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    for (String key : keys) {
                        String docId = key.substring(4);
                        idsToDelete.add(docId);
                    }
                }
                
                if (!idsToDelete.isEmpty()) {
                    vectorStore.delete(idsToDelete);
                    log.info("删除向量数据成功: docId={}, 共{}个分块", id, idsToDelete.size());
                }
            } catch (Exception e) {
                log.warn("删除向量数据失败: docId={}", id, e);
            }
        }

        boolean removed = this.removeById(id);

        updateKnowledgeBaseStats(document.getKbId());

        return removed;
    }

    @Override
    public DocumentVO getDocumentById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档ID不能为空");
        }
        Document document = this.getById(id);
//        if (document == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
//        }
        return convertToVO(document);
    }

    @Override
    public Page<DocumentVO> queryDocuments(DocumentQueryRequest request, Long userId) {
        if (request.getKbId() != null) {
            KnowledgeBase kb = knowledgeBaseService.getById(request.getKbId());
//            if (kb == null || !kb.getUserId().equals(userId)) {
//                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
//            }
        }
        Page<Document> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getKbId() != null, Document::getKbId, request.getKbId())
                .like(StringUtils.isNotBlank(request.getFileName()), Document::getFileName, request.getFileName())
                .eq(StringUtils.isNotBlank(request.getFileType()), Document::getFileType, request.getFileType())
                .eq(request.getStatus() != null, Document::getStatus, request.getStatus())
                .orderByDesc(Document::getCreateTime);
        Page<Document> docPage = this.page(page, wrapper);
        Page<DocumentVO> voPage = new Page<>(docPage.getCurrent(), docPage.getSize(), docPage.getTotal());
        voPage.setRecords(docPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public boolean updateDocumentStatus(Long id, Integer status, Integer chunkCount, String errorMsg) {
        Document document = this.getById(id);
        if (document == null) {
            return false;
        }
        document.setStatus(status);
        return this.updateById(document);
    }

    @Override
    public boolean deleteDocumentsByKbId(Long kbId, Long userId) {
        if (kbId == null) {
            return false;
        }
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getKbId, kbId);
        List<Document> documents = this.list(wrapper);
        
        for (Document document : documents) {
            deleteDocument(document.getId(), userId);
        }

        return this.remove(wrapper);
    }

    private void updateKnowledgeBaseStats(Long kbId) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getKbId, kbId)
                .eq(Document::getStatus, DocumentStatusEnum.COMPLETED.getCode());
        List<Document> successDocuments = this.list(wrapper);

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

    private DocumentVO convertToVO(Document document) {
        DocumentVO vo = new DocumentVO();
        BeanUtil.copyProperties(document, vo);
        DocumentStatusEnum statusEnum = DocumentStatusEnum.getByCode(document.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
