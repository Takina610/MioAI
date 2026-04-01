package com.mio.ai.customagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具实体类
 */
@Data
@TableName("mcp_tool")
public class McpTool implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 服务器名称
     */
    private String serverName;

    /**
     * 配置（JSON格式）
     */
    private String config;

    /**
     * 状态（0-未激活 1-正常 2-异常）
     */
    private Integer status;

    /**
     * 是否公开（0-私有 1-公开）
     */
    private Integer isPublic;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 最后使用时间
     */
    private Date lastUsedTime;

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

    @TableLogic
    private Integer isDeleted;
}
