package com.mio.ai.resource.service.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.log.AgentUsageLogQueryRequest;
import com.mio.ai.resource.model.entity.AgentUsageLog;
import com.mio.ai.resource.model.vo.log.AgentUsageLogVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志服务接口
 */
public interface AgentUsageLogService extends IService<AgentUsageLog> {

    /**
     * 记录使用日志
     */
    Long logUsage(AgentUsageLog log);

    /**
     * 分页查询使用日志
     */
    Page<AgentUsageLogVO> queryUsageLogs(AgentUsageLogQueryRequest request);

    /**
     * 统计智能体总使用次数
     */
    Long countByAgentId(Long agentId);

    /**
     * 统计智能体总Token消耗
     */
    Long sumTokensByAgentId(Long agentId);
}
