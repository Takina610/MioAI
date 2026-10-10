package com.mio.ai.resource.service.skill.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.framework.skillssh.SkillsShClient;
import com.mio.ai.resource.mapper.skill.SkillMapper;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.vo.skill.SkillsShSearchVO;
import com.mio.ai.resource.service.skill.SkillGithubImportService;
import com.mio.ai.resource.service.skill.SkillsShService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: skills.sh 搜索/安装实现。search 仅返回 source 为合法 GitHub 坐标的结果；
 *               安装复用 GitHub 导入服务（按仓库坐标定位 skillId 目录）。
 */
@Slf4j
@Service
public class SkillsShServiceImpl implements SkillsShService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final Pattern OWNER_REPO = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$");

    @Resource
    private SkillsShClient skillsShClient;

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillGithubImportService skillGithubImportService;

    @Override
    public SkillsShSearchVO search(Long userId, String query, int limit, int offset) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        int safeOffset = Math.max(offset, 0);
        SkillsShClient.SkillsShSearchResult result = skillsShClient.search(query, safeLimit, safeOffset);

        Map<String, Skill> installedByKey = findGithubSkills(userId).stream()
                .collect(Collectors.toMap(s -> installKey(s.getRepoOwner(), s.getRepoName(), s.getSkillPath()),
                        Function.identity(), (a, b) -> a));

        SkillsShSearchVO vo = new SkillsShSearchVO();
        vo.setQuery(query);
        vo.setTotal(result.total());
        vo.setSkills(result.skills().stream()
                .filter(entry -> {
                    int slash = entry.source().indexOf('/');
                    if (slash <= 0 || slash == entry.source().length() - 1) {
                        return false;
                    }
                    // skills.sh 的 owner 可能带点（skills.volces.com 这类非 GitHub 来源），过滤
                    String owner = entry.source().substring(0, slash);
                    String repo = entry.source().substring(slash + 1);
                    return !repo.contains("/") && OWNER_REPO.matcher(entry.source()).matches()
                            && entry.source().equals(owner + "/" + repo);
                })
                .map(entry -> {
                    int slash = entry.source().indexOf('/');
                    SkillsShSearchVO.SkillsShSkillVO item = new SkillsShSearchVO.SkillsShSkillVO();
                    item.setName(StrUtil.blankToDefault(entry.name(), entry.skillId()));
                    item.setOwner(entry.source().substring(0, slash));
                    item.setRepo(entry.source().substring(slash + 1));
                    item.setInstalls(entry.installs());
                    item.setRepoUrl("https://github.com/" + entry.source());
                    item.setInstalled(installedByKey.containsKey(
                            installKey(item.getOwner(), item.getRepo(), entry.skillId())));
                    return item;
                })
                .toList());
        return vo;
    }

    @Override
    public Long install(Long userId, String owner, String repo, String skillId) {
        Skill skill = skillGithubImportService.installFromRegistry(userId, owner, repo, skillId);
        log.info("skills.sh 安装完成: userId={}, {}/{} {}, skillId={}", userId, owner, repo, skillId, skill.getId());
        return skill.getId();
    }

    private List<Skill> findGithubSkills(Long userId) {
        return skillMapper.selectList(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getUserId, userId)
                .eq(Skill::getInstalled, 1)
                .isNotNull(Skill::getRepoOwner)
                .isNotNull(Skill::getRepoName));
    }

    /** 已安装判定键：owner/repo + 技能目录末段（skills.sh 的 skillId 即目录名） */
    private String installKey(String owner, String repo, String skillPath) {
        String dir = StrUtil.nullToEmpty(skillPath);
        String base = dir.isEmpty() ? "" : dir.substring(dir.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return (owner + "/" + repo + ":" + base).toLowerCase(Locale.ROOT);
    }
}
