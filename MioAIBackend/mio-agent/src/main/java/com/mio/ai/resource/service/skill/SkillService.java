package com.mio.ai.resource.service.skill;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.vo.skill.SkillVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能服务接口
 */
public interface SkillService extends IService<Skill> {

    /**
     * 删除技能（级联解除智能体绑定）
     */
    boolean deleteSkill(Long id, Long userId);

    /**
     * 技能详情（仅所有者可见）
     */
    SkillVO getSkillById(Long id, Long userId);

    /**
     * 分页查询本人技能
     */
    Page<SkillVO> querySkills(SkillQueryRequest request);

    /**
     * 安装技能：未安装时按仓库来源拉取内容并置为已安装
     */
    SkillVO installSkill(Long id, Long userId);

    /**
     * 卸载技能：仅去除已安装标记（保留元数据，可随时重新安装）
     */
    SkillVO uninstallSkill(Long id, Long userId);

    /**
     * 智能体已启用的技能（对话装配用，权限由绑定关系保证）
     */
    List<Skill> getEnabledSkillsForAgent(Long agentId);
}
