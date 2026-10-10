package com.mio.ai.resource.service.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseAddRequest;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseQueryRequest;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseUpdateRequest;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;

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
     * 级联删除知识库（文档向量/R2 文件/记录 + 智能体绑定 + 知识库本身），
     * 不做归属校验，权限由调用方保证（用户路径校验所有者，管理路径校验 admin 角色）
     */
    boolean deleteKnowledgeBaseCascade(Long id);

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
