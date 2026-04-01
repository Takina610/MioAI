package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.customagent.mapper.ToolCallLogMapper;
import com.mio.ai.customagent.model.dto.log.ToolCallLogQueryRequest;
import com.mio.ai.customagent.model.entity.ToolCallLog;
import com.mio.ai.customagent.model.enums.ToolCallStatusEnum;
import com.mio.ai.customagent.model.vo.ToolCallLogVO;
import com.mio.ai.customagent.service.ToolCallLogService;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志服务实现类
 */
@Service
public class ToolCallLogServiceImpl extends ServiceImpl<ToolCallLogMapper, ToolCallLog> implements ToolCallLogService {

    @Override
    public Long logToolCall(ToolCallLog log) {
        this.save(log);
        return log.getId();
    }

    @Override
    public Page<ToolCallLogVO> queryToolCallLogs(ToolCallLogQueryRequest request) {
        Page<ToolCallLog> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<ToolCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getAgentId() != null, ToolCallLog::getAgentId, request.getAgentId())
                .eq(request.getToolId() != null, ToolCallLog::getToolId, request.getToolId())
                .eq(request.getConversationId() != null, ToolCallLog::getConversationId, request.getConversationId())
                .eq(request.getStatus() != null, ToolCallLog::getStatus, request.getStatus())
                .orderByDesc(ToolCallLog::getCreateTime);
        Page<ToolCallLog> logPage = this.page(page, wrapper);
        Page<ToolCallLogVO> voPage = new Page<>(logPage.getCurrent(), logPage.getSize(), logPage.getTotal());
        voPage.setRecords(logPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Long countByToolId(Long toolId) {
        LambdaQueryWrapper<ToolCallLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolCallLog::getToolId, toolId);
        return this.count(wrapper);
    }

    private ToolCallLogVO convertToVO(ToolCallLog log) {
        ToolCallLogVO vo = new ToolCallLogVO();
        BeanUtil.copyProperties(log, vo);
        ToolCallStatusEnum statusEnum = ToolCallStatusEnum.getByCode(log.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
