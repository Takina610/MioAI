package com.mio.ai.bot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.bot.mapper.AgentMessageMapper;
import com.mio.ai.bot.mapper.AgentMessageVersionMapper;
import com.mio.ai.bot.model.entity.AgentMessageDO;
import com.mio.ai.bot.model.entity.AgentMessageVersionDO;
import com.mio.ai.bot.service.AgentMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AgentMessageServiceImpl implements AgentMessageService {

    private final AgentMessageMapper agentMessageMapper;
    private final AgentMessageVersionMapper versionMapper;

    public AgentMessageServiceImpl(AgentMessageMapper agentMessageMapper,
                                   AgentMessageVersionMapper versionMapper) {
        this.agentMessageMapper = agentMessageMapper;
        this.versionMapper = versionMapper;
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
        QueryWrapper<AgentMessageVersionDO> versionWrapper = new QueryWrapper<>();
        versionWrapper.eq("conversation_id", conversationId);
        versionMapper.delete(versionWrapper);
    }

    @Override
    public void deleteAfterSeq(String conversationId, long keepThroughSeq) {
        QueryWrapper<AgentMessageDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .gt("seq", keepThroughSeq);
        agentMessageMapper.delete(wrapper);
    }

    @Override
    public void deleteAfterSeqWithArchive(String conversationId, long keepThroughSeq, long groupKey) {
        List<AgentMessageDO> doomed = listByConversation(conversationId).stream()
                .filter(row -> row.getSeq() != null && row.getSeq() > keepThroughSeq)
                .toList();
        if (!doomed.isEmpty()) {
            archiveFirstPair(conversationId, doomed, groupKey);
        }
        deleteAfterSeq(conversationId, keepThroughSeq);
    }

    /** 归档被截断批次中的首个 (user, assistant) 轮次对 → 版本表（<n/n> 版本组来源） */
    private void archiveFirstPair(String conversationId, List<AgentMessageDO> doomed, long groupKey) {
        AgentMessageDO userRow = doomed.stream()
                .filter(row -> "user".equals(row.getRole()))
                .findFirst().orElse(null);
        if (userRow == null) {
            return;
        }
        AgentMessageDO assistantRow = doomed.stream()
                .filter(row -> "assistant".equals(row.getRole())
                        && row.getSeq() != null && row.getSeq() > userRow.getSeq())
                .findFirst().orElse(null);
        AgentMessageVersionDO version = new AgentMessageVersionDO();
        version.setConversationId(conversationId);
        version.setAgentId(userRow.getAgentId());
        version.setUserId(userRow.getUserId());
        version.setGroupKey(groupKey);
        version.setVersionIndex(nextVersionIndex(conversationId, groupKey));
        version.setUserBlocks(userRow.getBlocks());
        version.setAssistantBlocks(assistantRow != null ? assistantRow.getBlocks() : null);
        version.setPlan(assistantRow != null ? assistantRow.getPlan() : null);
        version.setDurationMs(assistantRow != null ? assistantRow.getDurationMs() : null);
        version.setCreateTime(new Date());
        try {
            versionMapper.insert(version);
        } catch (Exception e) {
            log.warn("版本归档失败（不影响截断）: {}", e.getMessage());
        }
    }

    private int nextVersionIndex(String conversationId, long groupKey) {
        QueryWrapper<AgentMessageVersionDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .eq("group_key", groupKey);
        Long count = versionMapper.selectCount(wrapper);
        return (count == null ? 0 : count.intValue()) + 1;
    }

    @Override
    public Map<Long, List<AgentMessageVersionDO>> versionsByGroup(String conversationId) {
        QueryWrapper<AgentMessageVersionDO> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .orderByAsc("version_index");
        return versionMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(AgentMessageVersionDO::getGroupKey));
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
