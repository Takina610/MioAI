package com.mio.ai.resource.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.AgentSkill;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 智能体-技能关联数据库操作
 */
@Mapper
public interface AgentSkillMapper extends BaseMapper<AgentSkill> {
}
