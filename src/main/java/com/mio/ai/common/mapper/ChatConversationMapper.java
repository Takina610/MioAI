package com.mio.ai.common.mapper;//package com.mio.ai.mapper;
//
//import com.baomidou.mybatisplus.core.mapper.BaseMapper;
//import com.mio.ai.model.entity.ChatConversation;
//import org.apache.ibatis.annotations.Mapper;
//import org.apache.ibatis.annotations.Select;
//
//import java.util.List;
//
///**
// * 聊天对话 Mapper
// */
//@Mapper
//public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
//
//    /**
//     * 按会话ID和时间戳倒序查询最近的N条消息
//     */
//    @Select("SELECT * FROM chat_conversation WHERE conversation_id = #{conversationId} ORDER BY `timestamp` DESC LIMIT #{limit}")
//    List<ChatConversation> selectRecentByConversationId(String conversationId, int limit);
//
//    /**
//     * 按会话ID查询所有消息
//     */
//    @Select("SELECT * FROM chat_conversation WHERE conversation_id = #{conversationId} ORDER BY `timestamp` ASC")
//    List<ChatConversation> selectByConversationId(String conversationId);
//
//    /**
//     * 按会话ID删除所有消息
//     */
//    @Select("DELETE FROM chat_conversation WHERE conversation_id = #{conversationId}")
//    int deleteByConversationId(String conversationId);
//}
