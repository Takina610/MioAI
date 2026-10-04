package com.mio.ai.bot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.bot.mapper.AgentMessageMapper;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.service.AgentMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class AgentMessageServiceImpl implements AgentMessageService {

    private final AgentMessageMapper agentMessageMapper;

    public AgentMessageServiceImpl(AgentMessageMapper agentMessageMapper) {
        this.agentMessageMapper = agentMessageMapper;
    }

    @Override
    public void append(AgentMessageDO message) {
        if (message.getConversationId() == null || message.getConversationId().isBlank()) {
            return;
        }
        message.setSeq(nextSeq(message.getConversationId()));
        if (message.getCreateTime() == null) {
            message.setCreateTime(new Date());
        }
        agentMessageMapper.insert(message);
    }

    @Override
    public List<AgentMessageDO> listByConversation(String conversationId) {
        QueryWrapper<AgentMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .orderByAsc("seq");
        return agentMessageMapper.selectList(wrapper);
    }

    @Override
    public void deleteByConversation(String conversationId) {
        QueryWrapper<AgentMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);
        agentMessageMapper.delete(wrapper);
    }

    @Override
    public void deleteAfterSeq(String conversationId, long keepThroughSeq) {
        QueryWrapper<AgentMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .gt("seq", keepThroughSeq);
        agentMessageMapper.delete(wrapper);
    }

    private int nextSeq(String conversationId) {
        QueryWrapper<AgentMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .orderByDesc("seq")
                .last("limit 1");
        AgentMessageDO last = agentMessageMapper.selectOne(wrapper);
        return last != null && last.getSeq() != null ? last.getSeq() + 1 : 1;
    }
}
