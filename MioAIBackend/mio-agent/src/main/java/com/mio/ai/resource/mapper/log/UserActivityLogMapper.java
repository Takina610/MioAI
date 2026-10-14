package com.mio.ai.resource.mapper.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.UserActivityLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户执行日志 Mapper
 */
@Mapper
public interface UserActivityLogMapper extends BaseMapper<UserActivityLog> {
}
