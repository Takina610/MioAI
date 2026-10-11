package com.mio.ai.resource.service.skill.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.framework.github.GithubClient;
import com.mio.ai.framework.github.GithubRepoRef;
import com.mio.ai.framework.github.GithubUrlParser;
import com.mio.ai.resource.mapper.skill.SkillMapper;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.enums.SkillStatusEnum;
import com.mio.ai.resource.model.vo.skill.GithubSkillPreviewVO;
import com.mio.ai.resource.model.vo.skill.GithubSkillVO;
import com.mio.ai.resource.service.skill.SkillGithubImportService;
import com.mio.ai.resource.service.skill.SkillMdSupport;
import com.mio.ai.resource.service.skill.SkillRateLimiter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 技能导入实现。发现规则：目录内含 SKILL.md 即视为一个技能，
 *               frontmatter 的 name/description 优先，缺省用目录名。
 *               安全约束：主机白名单（GithubClient）、链接路径校验（GithubUrlParser）、
 *               文件数/单文件/单技能总量上限、每用户限流；附属文件仅收文本类型。
 */
@Slf4j
@Service
public class SkillGithubImportServiceImpl implements SkillGithubImportService {

    /** 分支名含斜杠（feature/x）时逐级探测的最大层级 */
    private static final int MAX_REF_SEGMENTS = 3;
    private static final int PREVIEW_LIST_LIMIT = 20;

    private static final int PREVIEW_LIMIT_PER_HOUR = 60;
    private static final int IMPORT_LIMIT_PER_HOUR = 40;

    private static final Pattern REPO_SEGMENT = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    @Resource
    private GithubClient githubClient;

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillRateLimiter skillRateLimiter;

    /** 已解析引用：分支 + 链接中的子目录 + 完整文件树 */
    private record ResolvedRef(String branch, String subPath, GithubClient.GithubTree tree) {
    }

    @Override
    public GithubSkillPreviewVO preview(Long userId, String url) {
        skillRateLimiter.check("preview", userId, PREVIEW_LIMIT_PER_HOUR);
        GithubRepoRef parsed = GithubUrlParser.parse(url);
        ResolvedRef resolved = resolveRef(parsed);

        List<GithubSkillVO> skills = new ArrayList<>();
        long totalFound = 0;
        for (GithubClient.GithubTreeEntry entry : resolved.tree().entries()) {
            if (!isMarkerInSubPath(entry, resolved.subPath())) {
                continue;
            }
            totalFound++;
            if (skills.size() >= PREVIEW_LIST_LIMIT) {
                continue;
            }
            GithubSkillVO skillVo = describeSkill(parsed.owner(), parsed.repo(), resolved.branch(),
                    entry, resolved.tree().entries());
            if (skillVo != null) {
                skills.add(skillVo);
            }
        }

        GithubSkillPreviewVO vo = new GithubSkillPreviewVO();
        vo.setOwner(parsed.owner());
        vo.setRepo(parsed.repo());
        vo.setBranch(resolved.branch());
        vo.setTotalFound(totalFound);
        vo.setTruncated(totalFound > skills.size());
        vo.setSkills(skills);
        return vo;
    }

    @Override
    public List<GithubSkillVO> importSkills(Long userId, String url, List<String> skillPaths) {
        skillRateLimiter.check("import", userId, IMPORT_LIMIT_PER_HOUR);
        GithubRepoRef parsed = GithubUrlParser.parse(url);
        ResolvedRef resolved = resolveRef(parsed);
        if (resolved.tree().truncated()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仓库文件数过多，无法完整读取，请改用更精确的目录链接");
        }

        Map<String, GithubClient.GithubTreeEntry> blobByPath = blobByPath(resolved.tree());
        List<GithubSkillVO> registered = new ArrayList<>();
        for (String skillPath : new LinkedHashSet<>(skillPaths)) {
            GithubClient.GithubTreeEntry marker = blobByPath.get(skillPath);
            if (marker == null || !isMarkerInSubPath(marker, resolved.subPath())) {
                log.warn("跳过无效的技能路径: {}", skillPath);
                continue;
            }
            try {
                GithubSkillVO vo = registerSkill(userId, parsed.owner(), parsed.repo(),
                        resolved.branch(), marker, blobByPath);
                if (vo != null) {
                    registered.add(vo);
                }
            } catch (BusinessException e) {
                log.warn("登记技能失败: path={}, {}", skillPath, e.getMessage());
            } catch (Exception e) {
                log.error("登记技能异常: path={}", skillPath, e);
            }
        }
        log.info("GitHub 技能登记完成: userId={}, 成功 {}/{}", userId, registered.size(), skillPaths.size());
        return registered;
    }

    /** 预览单个技能：SKILL.md frontmatter + 目录内文件统计 */
    private GithubSkillVO describeSkill(String owner, String repo, String branch,
                                        GithubClient.GithubTreeEntry marker,
                                        List<GithubClient.GithubTreeEntry> treeEntries) {
        String skillDir = dirOf(marker.path());
        GithubSkillVO vo = new GithubSkillVO();
        vo.setPath(marker.path());
        vo.setFileCount(countFilesUnder(treeEntries, skillDir));
        vo.setTotalSize(treeEntries.stream()
                .filter(e -> "blob".equals(e.type()) && isUnderDir(e.path(), skillDir))
                .mapToLong(GithubClient.GithubTreeEntry::size)
                .sum());
        try {
            byte[] bytes = githubClient.fetchRawFile(owner, repo, branch, marker.path(), SkillMdSupport.MAX_FILE_SIZE);
            Map<String, String> frontmatter = SkillMdSupport.parseFrontmatter(new String(bytes, StandardCharsets.UTF_8));
            vo.setName(frontmatter.getOrDefault("name", baseName(skillDir)));            vo.setDescription(SkillMdSupport.descriptionOf(frontmatter, 500));
        } catch (Exception e) {
            log.warn("读取技能 frontmatter 失败，使用目录名: path={}, {}", marker.path(), e.getMessage());
            vo.setName(baseName(skillDir));
        }
        return vo;
    }

    /** 登记单个技能：只解析 frontmatter + 记录仓库坐标，不下载内容 */
    private GithubSkillVO registerSkill(Long userId, String owner, String repo, String branch,
                                        GithubClient.GithubTreeEntry marker,
                                        Map<String, GithubClient.GithubTreeEntry> blobByPath) {
        String skillDir = dirOf(marker.path());
        if (findRegistered(userId, owner, repo, skillDir) != null) {
            return null;
        }
        byte[] markerBytes = githubClient.fetchRawFile(owner, repo, branch, marker.path(), SkillMdSupport.MAX_FILE_SIZE);
        Map<String, String> frontmatter = SkillMdSupport.parseFrontmatter(
                new String(markerBytes, StandardCharsets.UTF_8));

        String name = frontmatter.getOrDefault("name", baseName(skillDir));
        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setName(SkillMdSupport.cleanName(name, 100));
        skill.setDescription(SkillMdSupport.cleanDescription(frontmatter, 500));
        skill.setRepoOwner(owner);
        skill.setRepoName(repo);
        skill.setRepoBranch(branch);
        skill.setSkillPath(skillDir);
        skill.setDocUrl(SkillMdSupport.buildDocUrl(owner, repo, branch, skillDir));
        skill.setSourceUrl(SkillMdSupport.truncate("https://github.com/" + owner + "/" + repo
                + "/tree/" + branch + (skillDir.isEmpty() ? "" : "/" + skillDir), 500));
        skill.setStatus(SkillStatusEnum.ACTIVE.getCode());
        skill.setInstalled(0);
        skillMapper.insert(skill);

        GithubSkillVO vo = new GithubSkillVO();
        vo.setPath(marker.path());
        vo.setName(skill.getName());
        vo.setDescription(skill.getDescription());
        vo.setFileCount(countFilesUnder(blobByPath.values().stream().toList(), skillDir));
        vo.setTotalSize(0);
        return vo;
    }

    @Override
    public void fetchContent(Skill skill) {
        String owner = skill.getRepoOwner();
        String repo = skill.getRepoName();
        String branch = skill.getRepoBranch();
        if (StrUtil.isBlank(owner) || StrUtil.isBlank(repo)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该技能没有仓库来源，无法拉取内容");
        }
        if (StrUtil.isBlank(branch)) {
            branch = githubClient.fetchRepoInfo(owner, repo).defaultBranch();
        }
        GithubClient.GithubTree tree = githubClient.fetchTree(owner, repo, branch);
        if (tree.truncated()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仓库文件数过多，无法完整读取");
        }
        Map<String, GithubClient.GithubTreeEntry> blobByPath = blobByPath(tree);
        String skillDir = StrUtil.nullToEmpty(skill.getSkillPath());
        GithubClient.GithubTreeEntry marker = blobByPath.get(markerPath(skillDir));
        if (marker == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "仓库内未找到该技能的 SKILL.md，可能已被移除");
        }

        byte[] markerBytes = githubClient.fetchRawFile(owner, repo, branch, marker.path(), SkillMdSupport.MAX_FILE_SIZE);
        String skillMd = new String(markerBytes, StandardCharsets.UTF_8);
        Map<String, String> frontmatter = SkillMdSupport.parseFrontmatter(skillMd);

        List<Map<String, String>> files = new ArrayList<>();
        long totalSize = markerBytes.length;
        for (GithubClient.GithubTreeEntry entry : blobByPath.values()) {
            if (entry.path().equals(marker.path()) || !isUnderDir(entry.path(), skillDir)
                    || !SkillMdSupport.isSupportedFile(entry.path())) {
                continue;
            }
            if (files.size() >= SkillMdSupport.MAX_FILES_PER_SKILL
                    || totalSize + entry.size() > SkillMdSupport.MAX_SKILL_TOTAL_SIZE) {
                log.warn("技能附属文件超出上限，截断: skill={}/{} {}", owner, repo, skillDir);
                break;
            }
            byte[] bytes = githubClient.fetchRawFile(owner, repo, branch, entry.path(), SkillMdSupport.MAX_FILE_SIZE);
            totalSize += bytes.length;
            Map<String, String> file = new LinkedHashMap<>();
            file.put("path", relativePath(entry.path(), skillDir));
            file.put("content", new String(bytes, StandardCharsets.UTF_8));
            files.add(file);
        }

        skill.setContent(skillMd);
        skill.setFiles(files.isEmpty() ? null : JacksonUtil.writeValueAsString(files));
        skill.setRepoBranch(branch);
        skill.setDocUrl(SkillMdSupport.buildDocUrl(owner, repo, branch, skillDir));
        // frontmatter 可能比登记时更新，一并刷新展示信息
        skill.setName(SkillMdSupport.cleanName(
                frontmatter.getOrDefault("name", StrUtil.blankToDefault(skill.getName(), baseName(skillDir))), 100));
        skill.setDescription(SkillMdSupport.cleanDescription(frontmatter, 500));
    }

    @Override
    public Skill installFromRegistry(Long userId, String owner, String repo, String skillId) {
        skillRateLimiter.check("install", userId, IMPORT_LIMIT_PER_HOUR);
        validateSegment(owner, "owner");
        validateSegment(repo, "repo");
        validateSegment(skillId, "skillId");

        String branch = githubClient.fetchRepoInfo(owner, repo).defaultBranch();
        GithubClient.GithubTree tree = githubClient.fetchTree(owner, repo, branch);
        if (tree.truncated()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仓库文件数过多，无法完整读取");
        }
        // 定位目录名为 skillId 的技能（如 skills/pdf/SKILL.md）；仓库本身即技能时兜底根目录
        List<GithubClient.GithubTreeEntry> markers = tree.entries().stream()
                .filter(e -> "blob".equals(e.type()) && SkillMdSupport.isSkillMarker(e.path()))
                .filter(e -> skillId.equalsIgnoreCase(baseName(dirOf(e.path())))
                        || (dirOf(e.path()).isEmpty() && skillId.equalsIgnoreCase(repo)))
                .toList();
        if (markers.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "仓库内未找到技能 " + skillId);
        }
        GithubClient.GithubTreeEntry marker = markers.stream()
                .reduce((a, b) -> a.path().length() <= b.path().length() ? a : b).orElseThrow();

        String skillDir = dirOf(marker.path());
        Skill exist = findRegistered(userId, owner, repo, skillDir);
        if (exist == null) {
            exist = new Skill();
            exist.setUserId(userId);
            exist.setRepoOwner(owner);
            exist.setRepoName(repo);
            exist.setRepoBranch(branch);
            exist.setSkillPath(skillDir);
            exist.setSourceUrl(SkillMdSupport.truncate("https://github.com/" + owner + "/" + repo
                    + "/tree/" + branch + (skillDir.isEmpty() ? "" : "/" + skillDir), 500));
            exist.setStatus(SkillStatusEnum.ACTIVE.getCode());
        }
        fetchContent(exist);
        exist.setInstalled(1);
        if (exist.getId() == null) {
            skillMapper.insert(exist);
        } else {
            skillMapper.updateById(exist);
        }
        return exist;
    }

    /**
     * 消解 ref 歧义：tree/blob 之后剩余段中分支可能占 1~3 段（feature/x），逐级探测，
     * 探测成功即认为该前缀是分支，其余为子路径
     */
    private ResolvedRef resolveRef(GithubRepoRef parsed) {
        if (parsed.kind() == GithubRepoRef.Kind.REPO) {
            String defaultBranch = githubClient.fetchRepoInfo(parsed.owner(), parsed.repo()).defaultBranch();
            GithubClient.GithubTree tree = githubClient.fetchTree(parsed.owner(), parsed.repo(), defaultBranch);
            return new ResolvedRef(defaultBranch, "", tree);
        }
        List<String> rest = parsed.restSegments();
        BusinessException lastError = null;
        for (int k = 1; k <= Math.min(MAX_REF_SEGMENTS, rest.size()); k++) {
            String candidateRef = String.join("/", rest.subList(0, k));
            String subPath = String.join("/", rest.subList(k, rest.size()));
            try {
                GithubClient.GithubTree tree = githubClient.fetchTree(parsed.owner(), parsed.repo(), candidateRef);
                if (parsed.kind() == GithubRepoRef.Kind.BLOB) {
                    boolean blobExists = tree.entries().stream()
                            .anyMatch(e -> "blob".equals(e.type()) && e.path().equals(subPath));
                    if (!blobExists) {
                        lastError = new BusinessException(ErrorCode.NOT_FOUND_ERROR, "链接未指向有效文件");
                        continue;
                    }
                } else if (!subPath.isEmpty()) {
                    boolean dirExists = tree.entries().stream().anyMatch(e -> isUnderDir(e.path(), subPath));
                    if (!dirExists) {
                        lastError = new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目录在该分支下不存在");
                        continue;
                    }
                }
                return new ResolvedRef(candidateRef, subPath, tree);
            } catch (BusinessException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                    "无法解析链接中的分支或路径，请检查链接是否正确: " + lastError.getMessage());
        }
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "无法解析链接中的分支或路径");
    }

    private boolean isMarkerInSubPath(GithubClient.GithubTreeEntry entry, String subPath) {
        return "blob".equals(entry.type()) && SkillMdSupport.isSkillMarker(entry.path())
                && isUnderDir(entry.path(), subPath);
    }

    private Map<String, GithubClient.GithubTreeEntry> blobByPath(GithubClient.GithubTree tree) {
        return tree.entries().stream()
                .filter(e -> "blob".equals(e.type()))
                .collect(Collectors.toMap(GithubClient.GithubTreeEntry::path, e -> e, (a, b) -> a, LinkedHashMap::new));
    }

    private int countFilesUnder(List<GithubClient.GithubTreeEntry> entries, String dir) {
        return (int) entries.stream()
                .filter(e -> "blob".equals(e.type()) && isUnderDir(e.path(), dir))
                .count();
    }

    /** 子路径前缀匹配（目录边界对齐，"doc" 不命中 "docs/..."） */
    private boolean isUnderDir(String path, String dir) {
        if (dir == null || dir.isEmpty()) {
            return true;
        }
        return path.equals(dir) || path.startsWith(dir + "/");
    }

    private String markerPath(String skillDir) {
        return skillDir.isEmpty() ? "SKILL.md" : skillDir + "/SKILL.md";
    }

    private String dirOf(String skillMdPath) {
        int slash = skillMdPath.lastIndexOf('/');
        return slash < 0 ? "" : skillMdPath.substring(0, slash);
    }

    private String relativePath(String path, String dir) {
        return dir.isEmpty() ? path : path.substring(dir.length() + 1);
    }

    private String baseName(String dir) {
        if (dir == null || dir.isEmpty()) {
            return "skill";
        }
        return dir.substring(dir.lastIndexOf('/') + 1);
    }

    private Skill findRegistered(Long userId, String owner, String repo, String skillDir) {
        return skillMapper.selectOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getUserId, userId)
                .eq(Skill::getRepoOwner, owner)
                .eq(Skill::getRepoName, repo)
                .eq(Skill::getSkillPath, StrUtil.nullToEmpty(skillDir))
                .last("LIMIT 1"));
    }

    private void validateSegment(String value, String field) {
        if (value == null || !REPO_SEGMENT.matcher(value).matches() || value.contains("..")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的仓库" + field + ": " + value);
        }
    }
}
