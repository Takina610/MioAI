package com.mio.ai.customagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.entity.Document;
import com.mio.ai.customagent.model.vo.DocumentVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档服务接口
 */
public interface DocumentService extends IService<Document> {

    /**
     * 添加文档
     */
    Long addDocument(DocumentAddRequest request);

    /**
     * 删除文档
     */
    boolean deleteDocument(Long id);

    /**
     * 根据ID获取文档
     */
    DocumentVO getDocumentById(Long id);

    /**
     * 分页查询文档
     */
    Page<DocumentVO> queryDocuments(DocumentQueryRequest request);

    /**
     * 更新文档处理状态
     */
    boolean updateDocumentStatus(Long id, Integer status, Integer chunkCount, String errorMsg);
}
