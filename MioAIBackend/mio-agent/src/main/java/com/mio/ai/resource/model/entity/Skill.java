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
     * 来源仓库 owner（GitHub 来源时有值）
     */
    private String repoOwner;

    /**
     * 来源仓库名（GitHub 来源时有值）
     */
    private String repoName;

    /**
     * 导入时的分支（GitHub 来源时有值）
     */
    private String repoBranch;

    /**
     * 技能在仓库内的目录（根目录为空串）
     */
    private String skillPath;

    /**
     * 仓库内 SKILL.md 的跳转链接
     */
    private String docUrl;

    /**
     * 是否已安装（0-仅登记 1-已安装，已安装才有内容）
     */
    private Integer installed;

    /**
     * 状态（0-禁用 1-正常）
     */
    private Integer status;

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
