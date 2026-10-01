package com.mio.ai.customagent.service.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseAddRequest;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseQueryRequest;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseUpdateRequest;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.model.vo.knowledge.KnowledgeBaseVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库服务接口
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 创建知识库
     */
    Long addKnowledgeBase(KnowledgeBaseAddRequest request, Long userId);

    /**
     * 更新知识库
     */
    boolean updateKnowledgeBase(KnowledgeBaseUpdateRequest request, Long userId);

    /**
     * 删除知识库
     */
    boolean deleteKnowledgeBase(Long id, Long userId);

    /**
     * 根据ID获取知识库
     */
    KnowledgeBaseVO getKnowledgeBaseById(Long id);

    /**
     * 分页查询知识库
     */
    Page<KnowledgeBaseVO> queryKnowledgeBases(KnowledgeBaseQueryRequest request);

    /**
     * 获取公开的知识库列表（广场）
     */
    Page<KnowledgeBaseVO> getPublicKnowledgeBases(long current, long size);
}
