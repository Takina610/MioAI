package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.customagent.mapper.RagRetrievalLogMapper;
import com.mio.ai.customagent.model.dto.log.RagRetrievalLogQueryRequest;
import com.mio.ai.customagent.model.entity.RagRetrievalLog;
import com.mio.ai.customagent.model.vo.RagRetrievalLogVO;
import com.mio.ai.customagent.service.RagRetrievalLogService;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志服务实现类
 */
@Service
public class RagRetrievalLogServiceImpl extends ServiceImpl<RagRetrievalLogMapper, RagRetrievalLog> implements RagRetrievalLogService {

    @Override
    public Long logRetrieval(RagRetrievalLog log) {
        this.save(log);
        return log.getId();
    }

    @Override
    public Page<RagRetrievalLogVO> queryRetrievalLogs(RagRetrievalLogQueryRequest request) {
        Page<RagRetrievalLog> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<RagRetrievalLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getAgentId() != null, RagRetrievalLog::getAgentId, request.getAgentId())
                .eq(request.getKbId() != null, RagRetrievalLog::getKbId, request.getKbId())
                .like(StringUtils.isNotBlank(request.getQuery()), RagRetrievalLog::getQuery, request.getQuery())
                .orderByDesc(RagRetrievalLog::getCreateTime);
        Page<RagRetrievalLog> logPage = this.page(page, wrapper);
        Page<RagRetrievalLogVO> voPage = new Page<>(logPage.getCurrent(), logPage.getSize(), logPage.getTotal());
        voPage.setRecords(logPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    private RagRetrievalLogVO convertToVO(RagRetrievalLog log) {
        RagRetrievalLogVO vo = new RagRetrievalLogVO();
        BeanUtil.copyProperties(log, vo);
        return vo;
    }
}
