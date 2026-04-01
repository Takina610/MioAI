package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.customagent.mapper.AgentUsageLogMapper;
import com.mio.ai.customagent.model.dto.log.AgentUsageLogQueryRequest;
import com.mio.ai.customagent.model.entity.AgentUsageLog;
import com.mio.ai.customagent.model.vo.AgentUsageLogVO;
import com.mio.ai.customagent.service.AgentUsageLogService;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志服务实现类
 */
@Service
public class AgentUsageLogServiceImpl extends ServiceImpl<AgentUsageLogMapper, AgentUsageLog> implements AgentUsageLogService {

    @Override
    public Long logUsage(AgentUsageLog log) {
        this.save(log);
        return log.getId();
    }

    @Override
    public Page<AgentUsageLogVO> queryUsageLogs(AgentUsageLogQueryRequest request) {
        Page<AgentUsageLog> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<AgentUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getAgentId() != null, AgentUsageLog::getAgentId, request.getAgentId())
                .eq(request.getUserId() != null, AgentUsageLog::getUserId, request.getUserId())
                .eq(request.getConversationId() != null, AgentUsageLog::getConversationId, request.getConversationId())
                .eq(request.getStatus() != null, AgentUsageLog::getStatus, request.getStatus())
                .orderByDesc(AgentUsageLog::getCreateTime);
        Page<AgentUsageLog> logPage = this.page(page, wrapper);
        Page<AgentUsageLogVO> voPage = new Page<>(logPage.getCurrent(), logPage.getSize(), logPage.getTotal());
        voPage.setRecords(logPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public Long countByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentUsageLog::getAgentId, agentId);
        return this.count(wrapper);
    }

    @Override
    public Long sumTokensByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentUsageLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentUsageLog::getAgentId, agentId);
        return this.list(wrapper).stream()
                .mapToLong(log -> (log.getInputTokens() != null ? log.getInputTokens() : 0)
                        + (log.getOutputTokens() != null ? log.getOutputTokens() : 0))
                .sum();
    }

    private AgentUsageLogVO convertToVO(AgentUsageLog log) {
        AgentUsageLogVO vo = new AgentUsageLogVO();
        BeanUtil.copyProperties(log, vo);
        vo.setTotalTokens((log.getInputTokens() != null ? log.getInputTokens() : 0)
                + (log.getOutputTokens() != null ? log.getOutputTokens() : 0));
        vo.setStatusDesc(log.getStatus() == 1 ? "成功" : "失败");
        return vo;
    }
}
