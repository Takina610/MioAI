package com.mio.ai.customagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.customagent.model.entity.AgentMcp;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP关联Mapper接口
 */
@Mapper
public interface AgentMcpMapper extends BaseMapper<AgentMcp> {
}
