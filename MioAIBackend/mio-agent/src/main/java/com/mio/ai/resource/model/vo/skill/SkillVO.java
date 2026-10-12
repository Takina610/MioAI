package com.mio.ai.resource.model.vo.skill;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能视图对象
 */
@Data
public class SkillVO implements Serializable {

    private Long id;

    /**
     * 创建者ID
     */
    private Long userId;

    /**
     * 创建者名称
     */
    private String userName;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述
     */
    private String description;

    /**
     * SKILL.md 内容
     */
    private String content;

    /**
     * 附属文件 [{path, content}]
     */
    private List<SkillFileVO> files;

    /**
     * 来源链接
     */
    private String sourceUrl;

    /**
     * 来源仓库 owner（GitHub 来源时有值）
     */
    private String repoOwner;

    /**
     * 来源仓库名
     */
    private String repoName;

    /**
     * 导入时的分支
     */
    private String repoBranch;

    /**
     * 技能在仓库内的目录
     */
    private String skillPath;

    /**
     * 仓库内 SKILL.md 的跳转链接
     */
    private String docUrl;

    /**
     * 是否已安装（0-仅登记 1-已安装）
     */
    private Integer installed;

    /**
     * 是否公开进技能广场（0-私有 1-公开）
     */
    private Integer isPublic;

    /**
     * 当前登录用户是否已拥有该技能（仅技能广场列表填充）
     */
    private Boolean owned;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    private Date createTime;

    private Date updateTime;

    @Data
    public static class SkillFileVO implements Serializable {
        private String path;
        private String content;
    }
}
