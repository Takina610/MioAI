package com.mio.ai.customagent.service.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.document.DocumentAddRequest;
import com.mio.ai.customagent.model.dto.document.DocumentQueryRequest;
import com.mio.ai.customagent.model.entity.Document;
import com.mio.ai.customagent.model.vo.knowledge.DocumentVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档服务接口
 */
public interface DocumentService extends IService<Document> {

    Long addDocument(DocumentAddRequest request, Long userId);

    boolean deleteDocument(Long id, Long userId);

    /**
     * 根据ID获取文档
     */
    DocumentVO getDocumentById(Long id);

    Page<DocumentVO> queryDocuments(DocumentQueryRequest request, Long userId);

    /**
     * 更新文档处理状态
     */
    boolean updateDocumentStatus(Long id, Integer status, Integer chunkCount, String errorMsg);

    /**
     * 删除知识库下的所有文档
     */
    boolean deleteDocumentsByKbId(Long kbId, Long userId);
}
