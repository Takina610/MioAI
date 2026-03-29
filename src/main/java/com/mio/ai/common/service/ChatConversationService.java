package com.mio.ai.common.service;//package com.mio.ai.service;
//
//import com.mio.ai.model.entity.ChatConversation;
//
//import java.util.List;
//
///**
// * 聊天对话服务接口
// */
//public interface ChatConversationService {
//
//    /**
//     * 保存聊天消息
//     */
//    void saveMessage(ChatConversation chatConversation);
//
//    /**
//     * 保存用户消息
//     */
//    void saveUserMessage(String conversationId, String content, String userId);
//
//    /**
//     * 保存助手消息
//     */
//    void saveAssistantMessage(String conversationId, String content);
//
//    /**
//     * 获取最近的N条消息
//     */
//    List<ChatConversation> getRecentMessages(String conversationId, int limit);
//
//    /**
//     * 获取会话的所有消息
//     */
//    List<ChatConversation> getAllMessages(String conversationId);
//
//    /**
//     * 清空会话的所有消息
//     */
//    void clearConversation(String conversationId);
//
//    /**
//     * 删除单条消息
//     */
//    void deleteMessage(Long messageId);
//
//    /**
//     * 获取单条消息
//     */
//    ChatConversation getMessageById(Long messageId);
//}
