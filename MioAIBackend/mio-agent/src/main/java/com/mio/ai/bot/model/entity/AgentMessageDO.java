package com.mio.ai.bot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 智能体消息完整持久化（自定义展示存储，区别于供模型上下文的 SPRING_AI_CHAT_MEMORY）：
 * <p>一条 user/assistant 消息一行，assistant 行携带完整内容块（blocks：文本/思考/工具
 * 调用及结果）、最终任务清单快照与耗时——刷新/分享/回看时按原样还原 Agent 工作过程。
 */
@Data
@TableName("agent_message")
public class AgentMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID（chat_conversation.conversation_id） */
    private String conversationId;

    private Long agentId;

    /** 空表示游客消息（不落库，见写入侧约定） */
    private Long userId;

    /** user / assistant */
    private String role;

    /** 会话内递增序号（排序用） */
    private Integer seq;

    /** 内容块 JSON 数组：[{type:text|thinking|tool, ...}] */
    private String blocks;

    /** 任务清单快照 JSON：[{index,description,status}]（assistant 行） */
    private String plan;

    /** 本条消息耗时（毫秒，assistant 行） */
    private Integer durationMs;

    /** 轮次组锚=该轮首个 user 行 seq；编辑重发的新行沿用被编辑行的组锚（版本组持久化） */
    private Long groupSeq;

    private Date createTime;
}
