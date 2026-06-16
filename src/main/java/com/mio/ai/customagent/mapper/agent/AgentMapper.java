package com.mio.ai.customagent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.customagent.model.entity.Agent;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体Mapper接口
 */
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}
