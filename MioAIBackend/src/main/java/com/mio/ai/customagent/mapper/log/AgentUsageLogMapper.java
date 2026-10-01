package com.mio.ai.customagent.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.customagent.model.entity.AgentUsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体使用日志Mapper接口
 */
@Mapper
public interface AgentUsageLogMapper extends BaseMapper<AgentUsageLog> {
}
