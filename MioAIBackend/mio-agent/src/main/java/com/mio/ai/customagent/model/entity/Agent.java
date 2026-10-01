package com.mio.ai.customagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体实体类
 */
@Data
@TableName("agent")
public class Agent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 智能体名称
     */
    private String name;

    /**
     * 智能体描述
     */
    private String description;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 智能体类型（0-内置 1-自定义）
     */
    private Integer type;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 状态（0-草稿 1-已发布 2-已禁用）
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
     * 版本号
     */
    private String version;

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
