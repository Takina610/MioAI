package com.mio.ai.resource.model.vo.skill;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 仓库中发现的技能
 */
@Data
public class GithubSkillVO implements Serializable {

    /**
     * SKILL.md 在仓库内的路径
     */
    private String path;

    /**
     * 技能名称（frontmatter.name 或目录名）
     */
    private String name;

    /**
     * 技能描述（frontmatter.description）
     */
    private String description;

    /**
     * 技能目录内文件总数（含 SKILL.md）
     */
    private int fileCount;

    /**
     * 技能目录内文件总大小（字节）
     */
    private long totalSize;
}
