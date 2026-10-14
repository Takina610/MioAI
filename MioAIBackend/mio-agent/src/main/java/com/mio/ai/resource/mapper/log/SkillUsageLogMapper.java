package com.mio.ai.resource.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.SkillUsageLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 技能使用日志 Mapper
 */
@Mapper
public interface SkillUsageLogMapper extends BaseMapper<SkillUsageLog> {
}
