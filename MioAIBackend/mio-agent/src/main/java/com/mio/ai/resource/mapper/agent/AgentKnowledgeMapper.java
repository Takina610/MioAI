package com.mio.ai.resource.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.AgentKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联Mapper接口
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledge> {
}
