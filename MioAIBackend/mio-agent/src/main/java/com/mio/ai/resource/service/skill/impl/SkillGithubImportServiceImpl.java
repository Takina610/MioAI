package com.mio.ai.resource.service.skill.impl;

import cn.hutool.core.util.StrUtil;
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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** 附属文件扩展名白名单（技能常含脚本/参考文档/模板） */
    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "md", "markdown", "txt", "py", "sh", "js", "ts", "mjs", "cjs", "json",
            "yaml", "yml", "toml", "csv", "tsv", "html", "htm", "css", "xml", "sql",
            "rb", "go", "rs", "java", "kt", "c", "cpp", "h", "hpp", "cs", "php",
            "swift", "bat", "ps1", "ini", "cfg", "conf", "properties", "proto");

    private static final Set<String> FILE_NAMES = Set.of(
            "dockerfile", "makefile", "license", ".gitignore", ".env.example");

    private static final long MAX_FILE_SIZE = 200L * 1024;
    private static final long MAX_SKILL_TOTAL_SIZE = 2L * 1024 * 1024;
    private static final int MAX_FILES_PER_SKILL = 30;
    private static final int PREVIEW_LIST_LIMIT = 20;
    /** 分支名含斜杠（feature/x）时逐级探测的最大层级 */
    private static final int MAX_REF_SEGMENTS = 3;

    private static final int PREVIEW_LIMIT_PER_HOUR = 60;
    private static final int IMPORT_LIMIT_PER_HOUR = 20;
    private static final DateTimeFormatter RATE_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Resource
    private GithubClient githubClient;

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 已解析引用：分支 + 链接中的子目录 + 完整文件树 */
    private record ResolvedRef(String branch, String subPath, GithubClient.GithubTree tree) {
    }

    @Override
    public GithubSkillPreviewVO preview(Long userId, String url) {
        checkRateLimit("preview", userId, PREVIEW_LIMIT_PER_HOUR);
        GithubRepoRef parsed = GithubUrlParser.parse(url);
        ResolvedRef resolved = resolveRef(parsed);

        List<GithubSkillVO> skills = new ArrayList<>();
        long totalFound = 0;
        for (GithubClient.GithubTreeEntry entry : resolved.tree().entries()) {
            if (!isSkillMarker(entry, resolved.subPath())) {
                continue;
            }
            totalFound++;
            if (skills.size() >= PREVIEW_LIST_LIMIT) {
                continue;
            }
            GithubSkillVO skillVo = describeSkill(parsed, resolved.branch(), entry, resolved.tree().entries());
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
        checkRateLimit("import", userId, IMPORT_LIMIT_PER_HOUR);
        GithubRepoRef parsed = GithubUrlParser.parse(url);
        ResolvedRef resolved = resolveRef(parsed);
        if (resolved.tree().truncated()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仓库文件数过多，无法完整读取，请改用更精确的目录链接");
        }

        Map<String, GithubClient.GithubTreeEntry> blobByPath = resolved.tree().entries().stream()
                .filter(e -> "blob".equals(e.type()))
                .collect(Collectors.toMap(GithubClient.GithubTreeEntry::path, e -> e, (a, b) -> a, LinkedHashMap::new));

        List<GithubSkillVO> imported = new ArrayList<>();
        for (String skillPath : new LinkedHashSet<>(skillPaths)) {
            GithubClient.GithubTreeEntry marker = blobByPath.get(skillPath);
            if (marker == null || !isSkillMarker(marker, resolved.subPath())) {
                log.warn("跳过无效的技能路径: {}", skillPath);
                continue;
            }
            try {
                GithubSkillVO vo = importSingleSkill(userId, parsed, resolved.branch(), marker, blobByPath);
                if (vo != null) {
                    imported.add(vo);
                }
            } catch (BusinessException e) {
                log.warn("导入技能失败: path={}, {}", skillPath, e.getMessage());
            } catch (Exception e) {
                log.error("导入技能异常: path={}", skillPath, e);
            }
        }
        log.info("GitHub 技能导入完成: userId={}, 成功 {}/{}", userId, imported.size(), skillPaths.size());
        return imported;
    }

    private GithubSkillVO importSingleSkill(Long userId, GithubRepoRef parsed, String branch,
                                            GithubClient.GithubTreeEntry marker,
                                            Map<String, GithubClient.GithubTreeEntry> blobByPath) {
        String skillDir = dirOf(marker.path());
        byte[] markerBytes = githubClient.fetchRawFile(parsed.owner(), parsed.repo(), branch,
                marker.path(), MAX_FILE_SIZE);
        String skillMd = new String(markerBytes, StandardCharsets.UTF_8);
        if (StrUtil.isBlank(skillMd)) {
            return null;
        }
        Map<String, String> frontmatter = parseFrontmatter(skillMd);

        // 收集技能目录内的附属文件（相对路径 -> 文本内容）
        List<Map<String, String>> files = new ArrayList<>();
        long totalSize = 0;
        for (GithubClient.GithubTreeEntry entry : blobByPath.values()) {
            if (entry.path().equals(marker.path()) || !isUnderDir(entry.path(), skillDir)
                    || !isSupportedFile(entry.path())) {
                continue;
            }
            if (files.size() >= MAX_FILES_PER_SKILL || totalSize + entry.size() > MAX_SKILL_TOTAL_SIZE) {
                log.warn("技能附属文件超出上限，截断: skill={}", marker.path());
                break;
            }
            byte[] bytes = githubClient.fetchRawFile(parsed.owner(), parsed.repo(), branch,
                    entry.path(), MAX_FILE_SIZE);
            totalSize += bytes.length;
            Map<String, String> file = new LinkedHashMap<>();
            file.put("path", relativePath(entry.path(), skillDir));
            file.put("content", new String(bytes, StandardCharsets.UTF_8));
            files.add(file);
        }

        Skill skill = new Skill();
        skill.setUserId(userId);
        skill.setName(frontmatter.getOrDefault("name", baseName(skillDir)));
        skill.setDescription(frontmatter.get("description"));
        skill.setContent(skillMd);
        skill.setFiles(files.isEmpty() ? null : JacksonUtil.writeValueAsString(files));
        skill.setSourceUrl("https://github.com/" + parsed.owner() + "/" + parsed.repo()
                + "/tree/" + branch + (skillDir.isEmpty() ? "" : "/" + skillDir));
        skill.setStatus(SkillStatusEnum.ACTIVE.getCode());
        skill.setIsPublic(0);
        skillMapper.insert(skill);

        GithubSkillVO vo = new GithubSkillVO();
        vo.setPath(marker.path());
        vo.setName(skill.getName());
        vo.setDescription(skill.getDescription());
        vo.setFileCount(files.size() + 1);
        vo.setTotalSize(totalSize + markerBytes.length);
        return vo;
    }

    /** 预览单个技能：SKILL.md frontmatter + 目录内文件统计 */
    private GithubSkillVO describeSkill(GithubRepoRef parsed, String branch,
                                        GithubClient.GithubTreeEntry marker,
                                        List<GithubClient.GithubTreeEntry> treeEntries) {
        String skillDir = dirOf(marker.path());
        GithubSkillVO vo = new GithubSkillVO();
        vo.setPath(marker.path());
        long totalSize = 0;
        int fileCount = 0;
        for (GithubClient.GithubTreeEntry entry : treeEntries) {
            if ("blob".equals(entry.type()) && isUnderDir(entry.path(), skillDir)) {
                fileCount++;
                totalSize += entry.size();
            }
        }
        vo.setFileCount(fileCount);
        vo.setTotalSize(totalSize);
        try {
            byte[] bytes = githubClient.fetchRawFile(parsed.owner(), parsed.repo(), branch,
                    marker.path(), MAX_FILE_SIZE);
            Map<String, String> frontmatter = parseFrontmatter(new String(bytes, StandardCharsets.UTF_8));
            vo.setName(frontmatter.getOrDefault("name", baseName(skillDir)));
            vo.setDescription(frontmatter.get("description"));
        } catch (Exception e) {
            log.warn("读取技能 frontmatter 失败，使用目录名: path={}, {}", marker.path(), e.getMessage());
            vo.setName(baseName(skillDir));
        }
        return vo;
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

    /** 是否为技能标记文件：SKILL.md（不区分大小写），且位于链接子目录内 */
    private boolean isSkillMarker(GithubClient.GithubTreeEntry entry, String subPath) {
        if (!"blob".equals(entry.type())) {
            return false;
        }
        String fileName = entry.path().substring(entry.path().lastIndexOf('/') + 1);
        if (!"skill.md".equalsIgnoreCase(fileName)) {
            return false;
        }
        return isUnderDir(entry.path(), subPath);
    }

    /** 子路径前缀匹配（目录边界对齐，"doc" 不命中 "docs/..."） */
    private boolean isUnderDir(String path, String dir) {
        if (dir == null || dir.isEmpty()) {
            return true;
        }
        return path.equals(dir) || path.startsWith(dir + "/");
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

    private boolean isSupportedFile(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (FILE_NAMES.contains(fileName)) {
            return true;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return false;
        }
        return FILE_EXTENSIONS.contains(fileName.substring(dot + 1));
    }

    /**
     * 解析 SKILL.md frontmatter（--- 包裹的 key: value 区块），key 统一小写
     */
    private Map<String, String> parseFrontmatter(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null) {
            return result;
        }
        String[] lines = content.split("\n", -1);
        if (lines.length < 2 || !"---".equals(lines[0].trim())) {
            return result;
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("---")) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private void checkRateLimit(String scene, Long userId, int limit) {
        String window = LocalDateTime.now().format(RATE_WINDOW);
        String key = "mio:skill-github:" + scene + ":" + userId + ":" + window;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, Duration.ofHours(2));
            }
            if (count != null && count > limit) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "操作过于频繁，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("技能导入限流检查异常，放行本次请求: {}", e.getMessage());
        }
    }
}
