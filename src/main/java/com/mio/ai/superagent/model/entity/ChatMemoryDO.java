package com.mio.ai.superagent.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/**
 * @author: Takina
 * @date: 2026/3/31 11:15
 * @description:
 */

@Data
@TableName("spring_ai_chat_memory")
public class ChatMemoryDO {
    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 文本内容
     */
    private String content;

    /**
     * USER、ASSISTANT
     */
    private String type;

    /**
     * 时间
     */
    private Timestamp timestamp;
}
