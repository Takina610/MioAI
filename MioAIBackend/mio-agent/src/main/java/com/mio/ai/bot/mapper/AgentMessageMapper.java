package com.mio.ai.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessageDO> {

    /** 每个智能体最近一条 assistant 行（左轨空闲情绪 = 最新会话收尾状态的持久化来源） */
    @Select("SELECT am.* FROM agent_message am "
            + "JOIN (SELECT agent_id, MAX(id) AS max_id FROM agent_message "
            + "      WHERE user_id = #{userId} AND role = 'assistant' GROUP BY agent_id) t "
            + "ON am.id = t.max_id")
    List<AgentMessageDO> selectLatestAssistantPerAgent(Long userId);
}
