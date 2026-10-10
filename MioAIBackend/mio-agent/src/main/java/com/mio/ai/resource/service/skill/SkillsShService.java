package com.mio.ai.resource.service.skill;

import com.mio.ai.resource.model.vo.skill.SkillsShSearchVO;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: skills.sh 技能市场搜索与一键安装
 */
public interface SkillsShService {

    /**
     * 搜索 skills.sh 公共目录并标注当前用户已安装状态
     */
    SkillsShSearchVO search(Long userId, String query, int limit, int offset);

    /**
     * 一键安装：在 owner/repo 内定位 skillId 技能，已登记则刷新内容，否则创建并安装
     *
     * @return 技能ID
     */
    Long install(Long userId, String owner, String repo, String skillId);
}
