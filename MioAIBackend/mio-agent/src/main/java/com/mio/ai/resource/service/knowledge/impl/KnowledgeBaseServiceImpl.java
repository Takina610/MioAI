package com.mio.ai.resource.service.knowledge.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.mapper.agent.AgentKnowledgeMapper;
import com.mio.ai.resource.mapper.knowledge.KnowledgeBaseMapper;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseAddRequest;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseQueryRequest;
import com.mio.ai.resource.model.dto.knowledgebase.KnowledgeBaseUpdateRequest;
import com.mio.ai.resource.model.entity.AgentKnowledge;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.enums.KnowledgeBaseStatusEnum;
import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;
import com.mio.ai.resource.service.knowledge.DocumentService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.knowledge.VectorizeTaskState;
import com.mio.ai.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库服务实现类
 */
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    AgentKnowledgeMapper agentKnowledgeMapper;

    @Autowired
    @Lazy
    DocumentService documentService;

    @Autowired
    UserService userService;

    @Autowired
    VectorizeTaskState vectorizeTaskState;

    @Override
    public Long addKnowledgeBase(KnowledgeBaseAddRequest request, Long userId) {
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库名称不能为空");
        }
        KnowledgeBase kb = new KnowledgeBase();
        BeanUtil.copyProperties(request, kb);
        kb.setUserId(userId);
        kb.setStatus(KnowledgeBaseStatusEnum.ACTIVE.getCode());
        kb.setDocumentCount(0);
        kb.setStorageSize(0L);
        normalizeChunkConfig(kb);
        this.save(kb);
        return kb.getId();
    }

    @Override
    public boolean updateKnowledgeBase(KnowledgeBaseUpdateRequest request, Long userId) {
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        KnowledgeBase kb = this.getById(request.getId());
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该知识库");
        }
        BeanUtil.copyProperties(request, kb);
        normalizeChunkConfig(kb);
        kb.setUpdateTime(new Date());
        return this.updateById(kb);
    }

    @Override
    public boolean deleteKnowledgeBase(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        KnowledgeBase kb = this.getById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该知识库");
        }

        return deleteKnowledgeBaseCascade(id);
    }

    @Override
    public boolean deleteKnowledgeBaseCascade(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }

        // 先置向量化中断标志：防在途任务在向量清完后又写入孤儿分块
        // （任务收尾会按 kbId 再兜底清一遍并消费标志）
        vectorizeTaskState.requestCancel(id);

        // 级联清理文档关联资源（向量数据、R2 文件、文档记录）
        documentService.deleteDocumentsByKbIdCascade(id);

        // 删除智能体与该知识库的关联记录
        agentKnowledgeMapper.delete(new LambdaQueryWrapper<AgentKnowledge>().eq(AgentKnowledge::getKbId, id));

        vectorizeTaskState.removeSnapshot(id);
        return this.removeById(id);
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBaseById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        KnowledgeBase kb = this.getById(id);
        if (kb == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
            return null;
        }
        return convertToVO(kb);
    }

    @Override
    public Page<KnowledgeBaseVO> queryKnowledgeBases(KnowledgeBaseQueryRequest request) {
        Page<KnowledgeBase> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(request.getName()), KnowledgeBase::getName, request.getName())
                .eq(request.getStatus() != null, KnowledgeBase::getStatus, request.getStatus())
                .eq(request.getUserId() != null, KnowledgeBase::getUserId, request.getUserId())
                .orderByDesc(KnowledgeBase::getCreateTime);
        Page<KnowledgeBase> kbPage = this.page(page, wrapper);
        Page<KnowledgeBaseVO> voPage = new Page<>(kbPage.getCurrent(), kbPage.getSize(), kbPage.getTotal());
        voPage.setRecords(kbPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Page<KnowledgeBaseVO> getPublicKnowledgeBases(long current, long size) {
        Page<KnowledgeBase> page = new Page<>(current, size);
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getIsPublic, 1)
                .eq(KnowledgeBase::getStatus, KnowledgeBaseStatusEnum.ACTIVE.getCode())
                .orderByDesc(KnowledgeBase::getCreateTime);
        Page<KnowledgeBase> kbPage = this.page(page, wrapper);
        Page<KnowledgeBaseVO> voPage = new Page<>(kbPage.getCurrent(), kbPage.getSize(), kbPage.getTotal());
        voPage.setRecords(kbPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    /**
     * 分块参数边界钳制：chunkSize 限制在 [MIN_CHUNK_SIZE, MAX_CHUNK_SIZE]，
     * chunkOverlap 不超过 chunkSize 的一半（重叠过半会导致内容大量重复）
     */
    public static final int MIN_CHUNK_SIZE = 200;
    public static final int MAX_CHUNK_SIZE = 2048;
    public static final int MAX_CHUNK_OVERLAP = 500;

    private void normalizeChunkConfig(KnowledgeBase kb) {
        if (kb.getChunkSize() != null) {
            kb.setChunkSize(Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, kb.getChunkSize())));
        }
        if (kb.getChunkOverlap() != null) {
            int half = (kb.getChunkSize() != null ? kb.getChunkSize() : 800) / 2;
            int cap = Math.min(MAX_CHUNK_OVERLAP, half);
            kb.setChunkOverlap(Math.max(0, Math.min(cap, kb.getChunkOverlap())));
        }
    }

    private KnowledgeBaseVO convertToVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtil.copyProperties(kb, vo);
        KnowledgeBaseStatusEnum statusEnum = KnowledgeBaseStatusEnum.getByCode(kb.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        if (kb.getUserId() != null) {
            try {
                String userName = userService.getUserNameById(kb.getUserId());
                vo.setUserName(userName != null ? userName : "未知用户");
            } catch (Exception e) {
                vo.setUserName("未知用户");
            }
        }
        return vo;
    }
}
