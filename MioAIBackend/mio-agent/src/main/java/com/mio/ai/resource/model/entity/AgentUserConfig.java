package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 内置智能体的用户级个性化配置（MioBot 内置但每人一套独立配置，字段均为覆盖语义：NULL=跟随内置默认）
 */
@Data
@TableName("agent_user_config")
public class AgentUserConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 内置智能体ID
     */
    private Long agentId;

    /**
     * 人设提示词覆盖
     */
    private String systemPrompt;

    /**
     * 开场白覆盖
     */
    private String openingMessage;

    /**
     * 推荐问题JSON数组覆盖
     */
    private String openingQuestions;

    /**
     * 采样温度覆盖（NULL=跟随内置默认）
     */
    private Double temperature;

    /**
     * 图标配置覆盖JSON
     */
    private String icon;

    /**
     * 是否发布到广场（0-未发布 1-已发布）：广场的 MioBot 槽位按当前用户装配
     */
    private Integer isPublic;

    @TableLogic
    private Integer isDeleted;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
