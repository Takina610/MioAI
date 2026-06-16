package com.mio.ai.customagent.service.log;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.log.ToolCallLogQueryRequest;
import com.mio.ai.customagent.model.entity.ToolCallLog;
import com.mio.ai.customagent.model.vo.log.ToolCallLogVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志服务接口
 */
public interface ToolCallLogService extends IService<ToolCallLog> {

    /**
     * 记录工具调用日志
     */
    Long logToolCall(ToolCallLog log);

    /**
     * 分页查询工具调用日志
     */
    Page<ToolCallLogVO> queryToolCallLogs(ToolCallLogQueryRequest request);

    /**
     * 统计工具调用次数
     */
    Long countByToolId(Long toolId);
}
