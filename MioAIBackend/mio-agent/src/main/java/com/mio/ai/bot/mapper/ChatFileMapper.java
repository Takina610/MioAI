package com.mio.ai.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.bot.model.entity.ChatFileDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatFileMapper extends BaseMapper<ChatFileDO> {
}
