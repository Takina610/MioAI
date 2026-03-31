package com.mio.ai.superagent.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mio.ai.superagent.mapper.ChatConversationDOMapper;
import com.mio.ai.superagent.model.entity.ChatConversationDO;
import com.mio.ai.superagent.model.vo.ChatVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        // 1. 生成标题（取前10字）
        String title = chatVO.getMessage().length() > 10
                ? chatVO.getMessage().substring(0, 10)
                : chatVO.getMessage();

        // 2. 根据 conversationId 查询是否已存在
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", chatVO.getChatId());
        ChatConversationDO exist = chatConversationDOMapper.selectOne(queryWrapper);

        if (exist == null) {
            ChatConversationDO newChat = new ChatConversationDO();
            newChat.setConversationId(chatVO.getChatId());
            newChat.setUserId(chatVO.getUserId());
            newChat.setAgentId(chatVO.getAgentId());
            newChat.setTitle(title);
            chatConversationDOMapper.insert(newChat);
        } else {
            exist.setTitle(title);
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
    public List<ChatConversationDO> getChats(Long userId, String agentId) {
        QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("agent_id", agentId);
        return chatConversationDOMapper.selectList(queryWrapper);
    }
}
