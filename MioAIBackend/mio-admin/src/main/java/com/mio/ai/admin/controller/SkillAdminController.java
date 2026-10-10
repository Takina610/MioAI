package com.mio.ai.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.admin.model.dto.SkillAdminUpdateRequest;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.enums.SkillStatusEnum;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.skill.SkillService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 管理员技能管理接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/admin/skills")
public class SkillAdminController {

    @Resource
    private SkillService skillService;

    @Resource
    private AgentSkillService agentSkillService;

    /**
     * 分页查询技能列表（管理员，含私有）
     */
    @PostMapping("/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<SkillVO>> listSkillsByPage(@RequestBody com.mio.ai.resource.model.dto.skill.SkillQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 50");

        QueryWrapper<Skill> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(request.getName())) {
            queryWrapper.like("name", request.getName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }
        queryWrapper.orderByDesc("update_time");

        Page<Skill> skillPage = skillService.page(new Page<>(current, size), queryWrapper);
        Page<SkillVO> voPage = new Page<>(skillPage.getCurrent(), skillPage.getSize(), skillPage.getTotal());
        voPage.setRecords(skillPage.getRecords().stream().map(skill -> {
            SkillVO vo = new SkillVO();
            // files 实体侧是 JSON 字符串、VO 侧是列表，BeanUtil 强转会炸，忽略转换错误
            BeanUtil.copyProperties(skill, vo, CopyOptions.create().setIgnoreError(true));
            SkillStatusEnum statusEnum = SkillStatusEnum.getByCode(skill.getStatus());
            vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return vo;
        }).toList());
        return ResultUtils.success(voPage);
    }

    /**
     * 技能详情（管理员）
     */
    @GetMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<SkillVO> getSkill(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "技能 id 不合法");
        Skill skill = skillService.getById(id);
        ThrowUtils.throwIf(skill == null, ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        SkillVO vo = new SkillVO();
        BeanUtil.copyProperties(skill, vo, CopyOptions.create().setIgnoreError(true));
        SkillStatusEnum statusEnum = SkillStatusEnum.getByCode(skill.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return ResultUtils.success(vo);
    }

    /**
     * 更新技能（管理员；content/files 不可改，走用户侧编辑）
     */
    @PutMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> updateSkill(@Valid @RequestBody SkillAdminUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        Skill skill = skillService.getById(request.getId());
        ThrowUtils.throwIf(skill == null, ErrorCode.NOT_FOUND_ERROR, "技能不存在");

        Skill update = new Skill();
        update.setId(request.getId());
        if (StrUtil.isNotBlank(request.getName())) {
            update.setName(request.getName().trim());
        }
        update.setDescription(request.getDescription());
        update.setStatus(request.getStatus());
        boolean result = skillService.updateById(update);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除技能（级联解除智能体绑定）
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteSkill(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "技能 id 不合法");
        Skill skill = skillService.getById(id);
        ThrowUtils.throwIf(skill == null, ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        agentSkillService.deleteBySkillId(id);
        boolean result = skillService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }
}
