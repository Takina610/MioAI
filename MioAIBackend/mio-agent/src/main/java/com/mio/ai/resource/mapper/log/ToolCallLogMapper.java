package com.mio.ai.resource.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.ToolCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用日志Mapper接口
 */
@Mapper
public interface ToolCallLogMapper extends BaseMapper<ToolCallLog> {
}
