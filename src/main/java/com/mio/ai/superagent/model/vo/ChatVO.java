package com.mio.ai.superagent.model.vo;

import lombok.Data;

/**
 * @author: Takina
 * @date: 2026/3/30 14:35
 * @description:
 */
@Data
public class ChatVO {
    /**
     * 会话ID
     */
    private String chatId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 消息内容
     */
    private String message;
}
