package com.mio.ai.superagent.mapper;

/**
 * @author: Takina
 * @date: 2026/3/30 14:47
 * @description:
 */

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.superagent.model.entity.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatConversationDOMapper extends BaseMapper<ChatConversationDO> {
}
