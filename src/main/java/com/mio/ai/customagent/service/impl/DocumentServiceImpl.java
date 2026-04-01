package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.DocumentMapper;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.entity.Document;
import com.mio.ai.customagent.model.enums.DocumentStatusEnum;
import com.mio.ai.customagent.model.vo.DocumentVO;
import com.mio.ai.customagent.service.DocumentService;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档服务实现类
 */
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Override
    public Long addDocument(DocumentAddRequest request) {
        if (request.getKbId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        if (StringUtils.isBlank(request.getFileName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        Document document = new Document();
        BeanUtil.copyProperties(request, document);
        document.setStatus(DocumentStatusEnum.PENDING.getCode());
        document.setChunkCount(0);
        this.save(document);
        return document.getId();
    }

    @Override
    public boolean deleteDocument(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档ID不能为空");
        }
        Document document = this.getById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        return this.removeById(id);
    }

    @Override
    public DocumentVO getDocumentById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档ID不能为空");
        }
        Document document = this.getById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在");
        }
        return convertToVO(document);
    }

    @Override
    public Page<DocumentVO> queryDocuments(DocumentQueryRequest request) {
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
        if (chunkCount != null) {
            document.setChunkCount(chunkCount);
        }
        if (errorMsg != null) {
            document.setErrorMsg(errorMsg);
        }
        return this.updateById(document);
    }

    private DocumentVO convertToVO(Document document) {
        DocumentVO vo = new DocumentVO();
        BeanUtil.copyProperties(document, vo);
        DocumentStatusEnum statusEnum = DocumentStatusEnum.getByCode(document.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
