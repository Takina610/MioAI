package com.mio.ai.common.service.impl;//package com.mio.ai.service.impl;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.mio.ai.mapper.ChatConversationMapper;
//import com.mio.ai.model.entity.ChatConversation;
//import com.mio.ai.service.ChatConversationService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
///**
// * 聊天对话服务实现
// */
//@Service
//@Slf4j
//public class ChatConversationServiceImpl implements ChatConversationService {
//
//    private final ChatConversationMapper chatConversationMapper;
//
//    public ChatConversationServiceImpl(ChatConversationMapper chatConversationMapper) {
//        this.chatConversationMapper = chatConversationMapper;
//    }
//
//    @Override
//    public void saveMessage(ChatConversation chatConversation) {
//        if (chatConversation.getTimestamp() == null) {
//            chatConversation.setTimestamp(System.currentTimeMillis());
//        }
//        if (chatConversation.getCreateTime() == null) {
//            chatConversation.setCreateTime(LocalDateTime.now());
//        }
//        if (chatConversation.getAgentId() == null) {
//            chatConversation.setAgentId("love-master");
//        }
//        chatConversationMapper.insert(chatConversation);
//        log.info("保存聊天消息: conversationId={}, type={}", chatConversation.getConversationId(), chatConversation.getType());
//    }
//
//    @Override
//    public void saveUserMessage(String conversationId, String content, String userId) {
//        ChatConversation message = ChatConversation.builder()
//                .conversationId(conversationId)
//                .userId(userId)
//                .content(content)
//                .type("USER")
//                .timestamp(System.currentTimeMillis())
//                .agentId("love-master")
//                .createTime(LocalDateTime.now())
//                .build();
//        chatConversationMapper.insert(message);
//        log.info("保存用户消息: conversationId={}, userId={}", conversationId, userId);
//    }
//
//    @Override
//    public void saveAssistantMessage(String conversationId, String content) {
//        ChatConversation message = ChatConversation.builder()
//                .conversationId(conversationId)
//                .content(content)
//                .type("ASSISTANT")
//                .timestamp(System.currentTimeMillis())
//                .agentId("love-master")
//                .createTime(LocalDateTime.now())
//                .build();
//        chatConversationMapper.insert(message);
//        log.info("保存助手消息: conversationId={}", conversationId);
//    }
//
//    @Override
//    public List<ChatConversation> getRecentMessages(String conversationId, int limit) {
//        List<ChatConversation> messages = chatConversationMapper.selectRecentByConversationId(conversationId, limit);
//        // 反转列表使其按时间升序排列
//        messages.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
//        log.info("获取最近消息: conversationId={}, count={}", conversationId, messages.size());
//        return messages;
//    }
//
//    @Override
//    public List<ChatConversation> getAllMessages(String conversationId) {
//        List<ChatConversation> messages = chatConversationMapper.selectByConversationId(conversationId);
//        log.info("获取所有消息: conversationId={}, count={}", conversationId, messages.size());
//        return messages;
//    }
//
//    @Override
//    public void clearConversation(String conversationId) {
//        chatConversationMapper.deleteByConversationId(conversationId);
//        log.info("清空会话: conversationId={}", conversationId);
//    }
//
//    @Override
//    public void deleteMessage(Long messageId) {
//        chatConversationMapper.deleteById(messageId);
//        log.info("删除消息: messageId={}", messageId);
//    }
//
//    @Override
//    public ChatConversation getMessageById(Long messageId) {
//        return chatConversationMapper.selectById(messageId);
//    }
//}
