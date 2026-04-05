package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.KnowledgeBaseMapper;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseAddRequest;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseQueryRequest;
import com.mio.ai.customagent.model.dto.knowledgebase.KnowledgeBaseUpdateRequest;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.model.enums.KnowledgeBaseStatusEnum;
import com.mio.ai.customagent.model.vo.KnowledgeBaseVO;
import com.mio.ai.customagent.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库服务实现类
 */
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

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
        return this.removeById(id);
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBaseById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        KnowledgeBase kb = this.getById(id);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
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

    private KnowledgeBaseVO convertToVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtil.copyProperties(kb, vo);
        KnowledgeBaseStatusEnum statusEnum = KnowledgeBaseStatusEnum.getByCode(kb.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
