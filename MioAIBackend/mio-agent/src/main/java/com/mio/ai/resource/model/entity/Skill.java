package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 智能体技能实体类（SKILL.md 内容 + 附属文件）
 */
@Data
@TableName("skill")
public class Skill implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建者ID
     */
    private Long userId;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述（供智能体判断何时使用）
     */
    private String description;

    /**
     * SKILL.md 内容
     */
    private String content;

    /**
     * 附属文件（JSON数组 [{path, content}]）
     */
    private String files;

    /**
     * 来源链接（GitHub 仓库/目录）
     */
    private String sourceUrl;

    /**
     * 状态（0-禁用 1-正常）
     */
    private Integer status;

    /**
     * 是否公开（0-私有 1-公开）
     */
    private Integer isPublic;

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
