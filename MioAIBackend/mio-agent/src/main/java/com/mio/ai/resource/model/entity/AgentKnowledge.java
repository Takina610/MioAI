package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联实体类
 */
@Data
@TableName("agent_knowledge")
public class AgentKnowledge implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 知识库ID
     */
    private Long kbId;

    /**
     * 检索配置（JSON格式）
     */
    private String retrievalConfig;

    /**
     * 是否启用（0-禁用 1-启用）
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
