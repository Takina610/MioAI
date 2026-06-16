package com.mio.ai.superagent.model.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "会话ID不能为空")
    @Size(max = 64, message = "会话ID长度不能超过64")
    private String chatId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 智能体ID
     */
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 20000, message = "消息内容长度不能超过20000")
    private String message;
}
