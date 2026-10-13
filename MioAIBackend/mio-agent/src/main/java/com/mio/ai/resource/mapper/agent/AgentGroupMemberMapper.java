package com.mio.ai.resource.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mio.ai.resource.model.entity.AgentGroupMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 群聊智能体成员关联 Mapper
 */
@Mapper
public interface AgentGroupMemberMapper extends BaseMapper<AgentGroupMember> {
}
