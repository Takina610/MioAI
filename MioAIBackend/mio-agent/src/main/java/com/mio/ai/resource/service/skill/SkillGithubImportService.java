package com.mio.ai.resource.service.skill;

import com.mio.ai.resource.model.vo.skill.GithubSkillPreviewVO;
import com.mio.ai.resource.model.vo.skill.GithubSkillVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 仓库技能导入服务
 */
public interface SkillGithubImportService {

    /**
     * 解析 GitHub 链接并发现仓库内的技能（含 SKILL.md frontmatter 信息），不落库
     */
    GithubSkillPreviewVO preview(Long userId, String url);

    /**
     * 导入选中的技能（下载 SKILL.md + 目录内附属文件，创建 skill 记录）
     *
     * @return 导入成功的技能
     */
    List<GithubSkillVO> importSkills(Long userId, String url, List<String> skillPaths);
}
