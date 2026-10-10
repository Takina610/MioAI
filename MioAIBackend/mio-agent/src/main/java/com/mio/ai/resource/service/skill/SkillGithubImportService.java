package com.mio.ai.resource.service.skill;

import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.vo.skill.GithubSkillPreviewVO;
import com.mio.ai.resource.model.vo.skill.GithubSkillVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 仓库技能导入服务。导入 = 仅登记元数据（不拉取内容，未安装态），
 *               内容在用户点击安装时按仓库坐标拉取。
 */
public interface SkillGithubImportService {

    /**
     * 解析 GitHub 链接并发现仓库内的技能（含 SKILL.md frontmatter 信息），不落库
     */
    GithubSkillPreviewVO preview(Long userId, String url);

    /**
     * 登记选中的技能（仅元数据 + 仓库坐标，installed=0），不拉取内容
     *
     * @return 登记成功的技能
     */
    List<GithubSkillVO> importSkills(Long userId, String url, List<String> skillPaths);

    /**
     * 按仓库坐标拉取 SKILL.md + 附属文件填充技能内容（不动 installed 标记）
     */
    void fetchContent(Skill skill);

    /**
     * skills.sh 一键安装：在仓库内定位名为 skillId 的技能目录（含 SKILL.md），
     * 已登记则刷新内容，否则创建记录并安装
     *
     * @return 技能记录
     */
    Skill installFromRegistry(Long userId, String owner, String repo, String skillId);
}
