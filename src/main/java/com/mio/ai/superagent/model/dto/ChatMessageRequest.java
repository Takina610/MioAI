package com.mio.ai.superagent.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天消息请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    /**
     * 会话ID
     */
    private String chatId;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 消息内容
     */
    private String content;
}
