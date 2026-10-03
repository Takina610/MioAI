package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP服务关联实体类
 */
@Data
@TableName("agent_mcp")
public class AgentMcp implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * MCP工具ID
     */
    private Long mcpId;

    /**
     * 是否启用（0-禁用 1-启用）
     */
    private Integer enabled;

    /**
     * 配置覆盖（JSON格式）
     */
    private String configOverride;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
