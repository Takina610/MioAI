package com.mio.ai.resource.service.skill.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.resource.mapper.agent.AgentSkillMapper;
import com.mio.ai.resource.mapper.skill.SkillMapper;
import com.mio.ai.resource.model.dto.skill.SkillAddRequest;
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.dto.skill.SkillUpdateRequest;
import com.mio.ai.resource.model.entity.AgentSkill;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.enums.SkillStatusEnum;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能服务实现
 */
@Slf4j
@Service
public class SkillServiceImpl extends ServiceImpl<SkillMapper, Skill> implements SkillService {

    @Resource
    private AgentSkillMapper agentSkillMapper;

    @Resource
    @Lazy
    private AgentSkillService agentSkillService;

    @Resource
    private UserService userService;

    @Override
    public Long addSkill(SkillAddRequest request, Long userId) {
        if (StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能名称不能为空");
        }
        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setName(request.getName().trim());
        skill.setDescription(request.getDescription());
        skill.setContent(request.getContent());
        skill.setFiles(serializeFiles(request.getFiles()));
        skill.setSourceUrl(request.getSourceUrl());
        skill.setStatus(SkillStatusEnum.ACTIVE.getCode());
        skill.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : 0);
        this.save(skill);
        return skill.getId();
    }

    @Override
    public boolean updateSkill(SkillUpdateRequest request, Long userId) {
        Skill exist = this.getById(request.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        if (!exist.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该技能");
        }
        Skill update = new Skill();
        update.setId(request.getId());
        if (StrUtil.isNotBlank(request.getName())) {
            update.setName(request.getName().trim());
        }
        update.setDescription(request.getDescription());
        update.setContent(request.getContent());
        if (request.getFiles() != null) {
            update.setFiles(serializeFiles(request.getFiles()));
        }
        update.setStatus(request.getStatus());
        update.setIsPublic(request.getIsPublic());
        update.setUpdateTime(new Date());
        // ignoreNullValue：未传字段保持原值
        Skill target = new Skill();
        BeanUtil.copyProperties(update, target, CopyOptions.create().setIgnoreNullValue(true));
        return this.updateById(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkill(Long id, Long userId) {
        Skill skill = this.getById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        if (!skill.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该技能");
        }
        // 级联解除智能体绑定
        agentSkillMapper.delete(new LambdaQueryWrapper<AgentSkill>().eq(AgentSkill::getSkillId, id));
        return this.removeById(id);
    }

    @Override
    public SkillVO getSkillById(Long id, Long userId) {
        Skill skill = this.getById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        boolean owner = userId != null && skill.getUserId().equals(userId);
        if (!owner && !Integer.valueOf(1).equals(skill.getIsPublic())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限查看该技能");
        }
        return convertToVO(skill);
    }

    @Override
    public Page<SkillVO> querySkills(SkillQueryRequest request) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getUserId, request.getUserId())
                .like(StrUtil.isNotBlank(request.getName()), Skill::getName, request.getName())
                .eq(request.getStatus() != null, Skill::getStatus, request.getStatus())
                .orderByDesc(Skill::getUpdateTime);
        return pageToVO(this.page(new Page<>(request.getCurrent(), request.getPageSize()), wrapper));
    }

    @Override
    public Page<SkillVO> getPublicSkills(long current, long size) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getIsPublic, 1)
                .eq(Skill::getStatus, SkillStatusEnum.ACTIVE.getCode())
                .orderByDesc(Skill::getUpdateTime);
        return pageToVO(this.page(new Page<>(current, size), wrapper));
    }

    @Override
    public List<Skill> getEnabledSkillsForAgent(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AgentSkill> bindingWrapper = new LambdaQueryWrapper<>();
        bindingWrapper.eq(AgentSkill::getAgentId, agentId).eq(AgentSkill::getEnabled, 1);
        List<AgentSkill> bindings = agentSkillService.list(bindingWrapper);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = bindings.stream().map(AgentSkill::getSkillId).toList();
        LambdaQueryWrapper<Skill> skillWrapper = new LambdaQueryWrapper<>();
        skillWrapper.in(Skill::getId, skillIds).eq(Skill::getStatus, SkillStatusEnum.ACTIVE.getCode());
        return this.list(skillWrapper);
    }

    private Page<SkillVO> pageToVO(Page<Skill> page) {
        Page<SkillVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    private SkillVO convertToVO(Skill skill) {
        SkillVO vo = new SkillVO();
        // files 在实体是 JSON 字符串、VO 是结构化列表，交给 copyProperties 会类型转换失败，先关掉容错再手动填充
        BeanUtil.copyProperties(skill, vo, CopyOptions.create().setIgnoreError(true));
        SkillStatusEnum statusEnum = SkillStatusEnum.getByCode(skill.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        vo.setFiles(deserializeFiles(skill.getFiles()));
        if (skill.getUserId() != null) {
            try {
                String userName = userService.getUserNameById(skill.getUserId());
                vo.setUserName(userName != null ? userName : "未知用户");
            } catch (Exception e) {
                vo.setUserName("未知用户");
            }
        }
        return vo;
    }

    private String serializeFiles(List<?> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        return JacksonUtil.writeValueAsString(files);
    }

    @SuppressWarnings("unchecked")
    private List<SkillVO.SkillFileVO> deserializeFiles(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<SkillVO.SkillFileVO> list = (List<SkillVO.SkillFileVO>) JacksonUtil.readListValue(json, SkillVO.SkillFileVO.class);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析技能附属文件失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
