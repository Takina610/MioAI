package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 智能体发布版本快照（每次发布/回滚生成一条，可查看历史配置并回滚）
 */
@Data
@TableName("agent_version")
public class AgentVersion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 版本号（v1.0 格式）
     */
    private String version;

    /**
     * 名称快照
     */
    private String name;

    /**
     * 描述快照
     */
    private String description;

    /**
     * 头像快照
     */
    private String avatar;

    /**
     * 图标快照JSON
     */
    private String icon;

    /**
     * 系统提示词快照
     */
    private String systemPrompt;

    /**
     * 开场白快照
     */
    private String openingMessage;

    /**
     * 推荐问题快照JSON数组
     */
    private String openingQuestions;

    /**
     * 采样温度快照
     */
    private Double temperature;

    /**
     * 资源绑定快照JSON（知识库/MCP/技能）
     */
    private String bindingsJson;

    /**
     * 回滚来源版本号（回滚产生的版本才有）
     */
    private String rollbackFrom;

    /**
     * 发布人ID
     */
    private Long userId;

    /**
     * 发布时间
     */
    private Date createTime;
}
