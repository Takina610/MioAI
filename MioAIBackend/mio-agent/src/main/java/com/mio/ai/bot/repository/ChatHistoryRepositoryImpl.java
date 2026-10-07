package com.mio.ai.bot.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.bot.mapper.ChatConversationDOMapper;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.model.vo.ChatVO;
import com.mio.ai.bot.util.ChatTitles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/30 14:48
 * @description:
 */
@Service
public class ChatHistoryRepositoryImpl implements ChatHistoryRepository {

    @Autowired
    ChatConversationDOMapper chatConversationDOMapper;

    @Override
    public void save(ChatVO chatVO) {
        // 1. 根据 conversationId 查询是否已存在
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", chatVO.getChatId());
        ChatConversationDO exist = chatConversationDOMapper.selectOne(queryWrapper);

        if (exist == null) {
            ChatConversationDO newChat = new ChatConversationDO();
            newChat.setConversationId(chatVO.getChatId());
            newChat.setUserId(chatVO.getUserId());
            newChat.setAgentId(chatVO.getAgentId());
            // 创建即落首条消息兜底标题：标题精化失败/用户中途离开时不再出现"新对话"占位
            newChat.setTitle(ChatTitles.fallbackTitle(chatVO.getMessage()));
            chatConversationDOMapper.insert(newChat);
        } else {
            exist.setUpdateTime(new Date());
            chatConversationDOMapper.updateById(exist);
        }
    }

    @Override
    public void clearByChatId(String chatId) {
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", chatId);
        chatConversationDOMapper.delete(queryWrapper);
    }

    @Override
    public List<ChatConversationDO> getChats(Long userId, Long agentId) {
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("agent_id", agentId);
        return chatConversationDOMapper.selectList(queryWrapper);
    }

    @Override
    public Page<ChatConversationDO> getChatsPage(Long userId, Long agentId, long current, long size) {
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("agent_id", agentId)
                .orderByDesc("update_time");
        return chatConversationDOMapper.selectPage(new Page<>(current, size), queryWrapper);
    }

    @Override
    public ChatConversationDO getChatByConversationId(String conversationId) {
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId);
        return chatConversationDOMapper.selectOne(queryWrapper);
    }
}
