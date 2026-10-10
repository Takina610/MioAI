package com.mio.ai.resource.service.skill;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.skill.SkillAddRequest;
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.dto.skill.SkillUpdateRequest;
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
     * 创建技能
     */
    Long addSkill(SkillAddRequest request, Long userId);

    /**
     * 更新技能（校验所有者）
     */
    boolean updateSkill(SkillUpdateRequest request, Long userId);

    /**
     * 删除技能（级联解除智能体绑定）
     */
    boolean deleteSkill(Long id, Long userId);

    /**
     * 技能详情（校验所有者或公开）
     */
    SkillVO getSkillById(Long id, Long userId);

    /**
     * 分页查询本人技能
     */
    Page<SkillVO> querySkills(SkillQueryRequest request);

    /**
     * 公开技能列表（供智能体绑定抽屉选择）
     */
    Page<SkillVO> getPublicSkills(long current, long size);

    /**
     * 智能体已启用的技能（对话装配用，权限由绑定关系保证）
     */
    List<Skill> getEnabledSkillsForAgent(Long agentId);
}
