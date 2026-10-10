package com.mio.ai.resource.model.vo.skill;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: skills.sh 搜索结果
 */
@Data
public class SkillsShSearchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    /**
     * 服务端命中的总数（用于分页加载）
     */
    private long total;

    private List<SkillsShSkillVO> skills;

    @Data
    public static class SkillsShSkillVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 技能名（即 skills.sh 的 skillId）
         */
        private String name;

        /**
         * 所在仓库 owner
         */
        private String owner;

        /**
         * 所在仓库名
         */
        private String repo;

        /**
         * 安装量
         */
        private Long installs;

        /**
         * 仓库主页链接
         */
        private String repoUrl;

        /**
         * 当前用户是否已安装该技能
         */
        private Boolean installed;
    }
}
