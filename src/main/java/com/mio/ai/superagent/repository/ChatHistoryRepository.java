package com.mio.ai.superagent.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.superagent.model.entity.ChatConversationDO;
import com.mio.ai.superagent.model.vo.ChatVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/30 14:18
 * @description:
 */
public interface ChatHistoryRepository {

    void save(ChatVO chatVO);

    void clearByChatId(String chatId);

    List<ChatConversationDO> getChats(Long userId, String agentId);

    Page<ChatConversationDO> getChatsPage(Long userId, String agentId, long current, long size);

    ChatConversationDO getChatByConversationId(String conversationId);
}
