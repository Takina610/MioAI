CREATE TABLE IF NOT EXISTS chat_conversation (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id` VARCHAR(64) NULL COMMENT '用户ID（可空，方便多用户）',
    `agent_id` VARCHAR(64) DEFAULT 'love-master' COMMENT '智能体ID：恋爱大师',
    `content` LONGTEXT NOT NULL COMMENT '消息内容',
    `type` VARCHAR(20) NOT NULL COMMENT '消息类型：USER / ASSISTANT / SYSTEM / TOOL',
    `timestamp` BIGINT NOT NULL COMMENT '时间戳（毫秒，更通用）',
    `extra` JSON NULL COMMENT '扩展字段：情绪、场景、工具调用记录',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `IDX_CONVERSATION_TIMESTAMP` (`conversation_id`, `timestamp`),
    INDEX `IDX_USER_ID` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Spring AI 恋爱大师智能体聊天记忆表';