package com.mio.ai.bot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 消息历史版本归档：编辑用户消息/重新生成被截断的旧轮次 (user, assistant) 对，
 * 按 group_key（轮次组锚）聚合——前端 &lt;n/n&gt; 版本组的持久化来源。
 */
@Data
@TableName("agent_message_version")
public class AgentMessageVersionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private Long agentId;

    private Long userId;

    /** 版本组锚=该轮 user 行的 group_seq */
    private Long groupKey;

    /** 组内序号，越大越新 */
    private Integer versionIndex;

    /** 旧版本用户消息 blocks（编辑产生） */
    private String userBlocks;

    /** 旧版本回复 blocks */
    private String assistantBlocks;

    private String plan;

    private Integer durationMs;

    private Date createTime;
}
