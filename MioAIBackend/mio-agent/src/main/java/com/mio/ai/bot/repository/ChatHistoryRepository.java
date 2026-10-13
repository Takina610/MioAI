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

    List<ChatConversationDO> getChats(Long userId, Long agentId);

    Page<ChatConversationDO> getChatsPage(Long userId, Long agentId, long current, long size);

    /**
     * 分页获取会话列表（keyword 非空时按标题模糊过滤）
     */
    Page<ChatConversationDO> getChatsPage(Long userId, Long agentId, long current, long size, String keyword);

    ChatConversationDO getChatByConversationId(String conversationId);

    /** 更新会话置顶标记（pinned 0/1） */
    void updatePinned(Long id, int pinned);
}
