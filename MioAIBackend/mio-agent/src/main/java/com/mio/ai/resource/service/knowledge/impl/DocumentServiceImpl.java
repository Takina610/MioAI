package com.mio.ai.resource.service.knowledge.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.R2Util;
import com.mio.ai.resource.mapper.knowledge.DocumentMapper;
import com.mio.ai.resource.model.dto.document.DocumentAddRequest;
import com.mio.ai.resource.model.dto.document.DocumentQueryRequest;
import com.mio.ai.resource.model.entity.Document;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.enums.DocumentStatusEnum;
import com.mio.ai.resource.model.vo.knowledge.DocumentVO;
import com.mio.ai.resource.service.knowledge.DocumentCleanupService;
import com.mio.ai.resource.service.knowledge.DocumentService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Resource
    @Lazy
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private R2Util r2Util;

    @Resource
    @Lazy
    private DocumentCleanupService documentCleanupService;

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

        // 向量清理按 metadata.docId 过滤删除，与文档状态无关（半途失败的文档也可能有残留分块）
        documentCleanupService.deleteVectorsByDocId(id);

        boolean removed = this.removeById(id);

        documentCleanupService.updateKnowledgeBaseStats(document.getKbId());

        return removed;
    }

    @Override
    public DocumentVO getDocumentById(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档ID不能为空");
        }
        Document document = this.getById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(document.getKbId());
        boolean owner = kb != null && userId != null && userId.equals(kb.getUserId());
        if (!owner && (kb == null || !Integer.valueOf(1).equals(kb.getIsPublic()))) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该文档");
        }
        return convertToVO(document);
    }

    @Override
    public Page<DocumentVO> queryDocuments(DocumentQueryRequest request, Long userId) {
        Long scopedUserId = userId != null ? userId : request.getUserId();
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        if (request.getKbId() != null) {
            // 指定知识库：校验所有者或公开知识库
            KnowledgeBase kb = knowledgeBaseService.getById(request.getKbId());
            if (kb == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
            }
            boolean owner = scopedUserId != null && scopedUserId.equals(kb.getUserId());
            if (!owner && !Integer.valueOf(1).equals(kb.getIsPublic())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该知识库下的文档");
            }
            wrapper.eq(Document::getKbId, request.getKbId());
        } else {
            // 未指定知识库：只允许查询本人知识库下的文档
            List<Long> ownKbIds = knowledgeBaseService.list().stream()
                    .filter(kb -> scopedUserId != null && scopedUserId.equals(kb.getUserId()))
                    .map(KnowledgeBase::getId)
                    .toList();
            if (ownKbIds.isEmpty()) {
                return new Page<>(request.getCurrent(), request.getPageSize());
            }
            wrapper.in(Document::getKbId, ownKbIds);
        }
        wrapper.like(StringUtils.isNotBlank(request.getFileName()), Document::getFileName, request.getFileName())
                .eq(StringUtils.isNotBlank(request.getFileType()), Document::getFileType, request.getFileType())
                .eq(request.getStatus() != null, Document::getStatus, request.getStatus())
                .orderByDesc(Document::getCreateTime);
        Page<Document> page = new Page<>(request.getCurrent(), request.getPageSize());
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

    private DocumentVO convertToVO(Document document) {
        DocumentVO vo = new DocumentVO();
        BeanUtil.copyProperties(document, vo);
        DocumentStatusEnum statusEnum = DocumentStatusEnum.getByCode(document.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
