package com.mio.ai.common.model.entity;//package com.mio.ai.model.entity;
//
//import com.baomidou.mybatisplus.annotation.IdType;
//import com.baomidou.mybatisplus.annotation.TableId;
//import com.baomidou.mybatisplus.annotation.TableName;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.time.LocalDateTime;
//
///**
// * 聊天对话实体
// */
//@Data
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//@TableName("chat_conversation")
//public class ChatConversation {
//
//    @TableId(type = IdType.AUTO)
//    private Long id;
//
//    /**
//     * 会话ID
//     */
//    private String conversationId;
//
//    /**
//     * 用户ID（可空，方便多用户）
//     */
//    private String userId;
//
//    /**
//     * 智能体ID：恋爱大师
//     */
//    private String agentId;
//
//    /**
//     * 消息内容
//     */
//    private String content;
//
//    /**
//     * 消息类型：USER / ASSISTANT / SYSTEM / TOOL
//     */
//    private String type;
//
//    /**
//     * 时间戳（毫秒）
//     */
//    private Long timestamp;
//
//    /**
//     * 扩展字段：情绪、场景、工具调用记录
//     */
//    private String extra;
//
//    /**
//     * 创建时间
//     */
//    private LocalDateTime createTime;
//}
