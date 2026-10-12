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
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.entity.AgentSkill;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.enums.SkillStatusEnum;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.skill.SkillGithubImportService;
import com.mio.ai.resource.service.skill.SkillService;
import com.mio.ai.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能服务实现。技能只有本人可见；安装 = 拉取内容并打标记，卸载 = 去除标记保留元数据。
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
    @Lazy
    private SkillGithubImportService skillGithubImportService;

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSkill(Long id, Long userId) {
        Skill skill = this.getById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        checkOwner(skill, userId);
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
        checkOwner(skill, userId);
        return convertToVO(skill);
    }

    @Override
    public Page<SkillVO> querySkills(SkillQueryRequest request) {
        // 分页参数钳制：pageSize 不设上限会被一次请求拉全表（DoS）
        long current = Math.max(request.getCurrent(), 1);
        long pageSize = Math.min(Math.max(request.getPageSize(), 1), 200);
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getUserId, request.getUserId())
                .like(StrUtil.isNotBlank(request.getName()), Skill::getName, request.getName())
                .eq(request.getStatus() != null, Skill::getStatus, request.getStatus())
                .orderByDesc(Skill::getUpdateTime);
        return pageToVO(this.page(new Page<>(current, pageSize), wrapper));
    }

    @Override
    public SkillVO installSkill(Long id, Long userId) {
        Skill skill = this.getById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        checkOwner(skill, userId);
        if (!Integer.valueOf(1).equals(skill.getInstalled())) {
            if (StrUtil.isBlank(skill.getContent())) {
                // 内容为空时必须从仓库来源拉取
                skillGithubImportService.fetchContent(skill);
            }
            skill.setInstalled(1);
            skill.setUpdateTime(new Date());
            this.updateById(skill);
        }
        return convertToVO(skill);
    }

    @Override
    public SkillVO uninstallSkill(Long id, Long userId) {
        Skill skill = this.getById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        checkOwner(skill, userId);
        if (!Integer.valueOf(0).equals(skill.getInstalled())) {
            skill.setInstalled(0);
            skill.setUpdateTime(new Date());
            this.updateById(skill);
        }
        return convertToVO(skill);
    }

    @Override
    public Page<SkillVO> queryPlazaSkills(long current, long pageSize, Long viewerId) {
        // 分页参数钳制，与本人列表同一上限
        long safeCurrent = Math.max(current, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), 200);
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getStatus, SkillStatusEnum.ACTIVE.getCode())
                .eq(Skill::getInstalled, 1)
                // GitHub 来源天然公开；自导入技能需本人显式公开
                .and(w -> w.eq(Skill::getIsPublic, 1).or().isNotNull(Skill::getRepoOwner))
                .orderByDesc(Skill::getUpdateTime);
        Page<Skill> page = this.page(new Page<>(safeCurrent, safePageSize), wrapper);

        Set<String> ownedRepoKeys = new HashSet<>();
        Set<String> ownedNames = new HashSet<>();
        if (viewerId != null) {
            for (Skill mine : this.list(new LambdaQueryWrapper<Skill>().eq(Skill::getUserId, viewerId))) {
                if (StrUtil.isNotBlank(mine.getRepoOwner())) {
                    ownedRepoKeys.add(repoKey(mine));
                } else {
                    ownedNames.add(mine.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
        boolean checkOwned = viewerId != null;

        Page<SkillVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(skill -> {
            SkillVO vo = convertToVO(skill);
            // 广场只露元数据，不携带正文与附属文件
            vo.setContent(null);
            vo.setFiles(null);
            vo.setOwned(checkOwned && isOwned(ownedRepoKeys, ownedNames, skill));
            return vo;
        }).toList());
        return voPage;
    }

    @Override
    public SkillVO updateSkillPublic(Long id, Long userId, boolean isPublic) {
        Skill skill = this.getById(id);
        if (skill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        checkOwner(skill, userId);
        if (StrUtil.isNotBlank(skill.getRepoOwner())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "GitHub 来源技能本身公开，无需设置");
        }
        if (!Integer.valueOf(1).equals(skill.getInstalled())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "技能未安装，没有可分享的内容");
        }
        skill.setIsPublic(isPublic ? 1 : 0);
        skill.setUpdateTime(new Date());
        this.updateById(skill);
        return convertToVO(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkillVO installFromPlaza(Long id, Long userId) {
        Skill source = this.getById(id);
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "技能不存在");
        }
        if (!Integer.valueOf(1).equals(source.getStatus()) || !Integer.valueOf(1).equals(source.getInstalled())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该技能不可安装");
        }
        boolean repoSourced = StrUtil.isNotBlank(source.getRepoOwner());
        // 防止凭 id 摸取未公开的自导入技能
        if (!repoSourced && !Integer.valueOf(1).equals(source.getIsPublic())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "该技能未公开");
        }
        Set<String> ownedRepoKeys = new HashSet<>();
        Set<String> ownedNames = new HashSet<>();
        for (Skill mine : this.list(new LambdaQueryWrapper<Skill>().eq(Skill::getUserId, userId))) {
            if (StrUtil.isNotBlank(mine.getRepoOwner())) {
                ownedRepoKeys.add(repoKey(mine));
            } else {
                ownedNames.add(mine.getName().toLowerCase(Locale.ROOT));
            }
        }
        if (isOwned(ownedRepoKeys, ownedNames, source)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已拥有该技能，无需重复安装");
        }

        Skill copy = new Skill();
        copy.setUserId(userId);
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setContent(source.getContent());
        copy.setFiles(source.getFiles());
        copy.setSourceUrl(source.getSourceUrl());
        copy.setRepoOwner(source.getRepoOwner());
        copy.setRepoName(source.getRepoName());
        copy.setRepoBranch(source.getRepoBranch());
        copy.setSkillPath(source.getSkillPath());
        copy.setDocUrl(source.getDocUrl());
        copy.setInstalled(1);
        copy.setIsPublic(0);
        copy.setStatus(SkillStatusEnum.ACTIVE.getCode());
        this.save(copy);
        return convertToVO(copy);
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
        // 未安装的技能没有内容，不参与对话装配
        LambdaQueryWrapper<Skill> skillWrapper = new LambdaQueryWrapper<>();
        skillWrapper.in(Skill::getId, skillIds)
                .eq(Skill::getStatus, SkillStatusEnum.ACTIVE.getCode())
                .eq(Skill::getInstalled, 1);
        return this.list(skillWrapper);
    }

    private void checkOwner(Skill skill, Long userId) {
        if (userId == null || skill.getUserId() == null || !skill.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该技能");
        }
    }

    /** 已拥有判定：GitHub 来源按仓库坐标（owner/repo:目录末段），自导入按名称 */
    private boolean isOwned(Set<String> ownedRepoKeys, Set<String> ownedNames, Skill skill) {
        if (StrUtil.isNotBlank(skill.getRepoOwner())) {
            return ownedRepoKeys.contains(repoKey(skill));
        }
        return skill.getName() != null && ownedNames.contains(skill.getName().toLowerCase(Locale.ROOT));
    }

    private String repoKey(Skill skill) {
        String dir = StrUtil.nullToEmpty(skill.getSkillPath());
        String base = dir.isEmpty() ? "" : dir.substring(dir.lastIndexOf('/') + 1);
        return (skill.getRepoOwner() + "/" + skill.getRepoName() + ":" + base).toLowerCase(Locale.ROOT);
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
