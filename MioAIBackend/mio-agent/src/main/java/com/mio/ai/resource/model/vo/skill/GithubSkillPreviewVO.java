package com.mio.ai.resource.model.vo.skill;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 技能导入预览结果
 */
@Data
public class GithubSkillPreviewVO implements Serializable {

    private String owner;

    private String repo;

    /**
     * 解析出的分支
     */
    private String branch;

    /**
     * 发现的技能总数（列表截断前的数量）
     */
    private long totalFound;

    /**
     * 列表是否因超出上限被截断
     */
    private boolean truncated;

    private List<GithubSkillVO> skills;
}
