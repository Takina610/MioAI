package com.mio.ai.bot.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.bot.model.entity.ChatConversationDO;
import com.mio.ai.bot.model.vo.ChatVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/30 14:18
 * @description:
 */
public interface ChatHistoryRepository {

    void save(ChatVO chatVO);

    void clearByChatId(String chatId);

    List<ChatConversationDO> getChats(Long userId);

    Page<ChatConversationDO> getChatsPage(Long userId, long current, long size);

    ChatConversationDO getChatByConversationId(String conversationId);
}
