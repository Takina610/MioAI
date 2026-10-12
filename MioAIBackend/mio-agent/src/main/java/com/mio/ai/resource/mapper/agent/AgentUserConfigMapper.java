package com.mio.ai.resource.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.AgentUserConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 内置智能体用户级配置Mapper
 */
@Mapper
public interface AgentUserConfigMapper extends BaseMapper<AgentUserConfig> {
}
