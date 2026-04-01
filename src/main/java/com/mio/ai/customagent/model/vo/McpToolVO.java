package com.mio.ai.customagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具视图对象
 */
@Data
public class McpToolVO implements Serializable {

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
     * 状态
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 是否公开
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
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
