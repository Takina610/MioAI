package com.mio.ai.resource.service.knowledge.impl;

import com.mio.ai.common.common.FileType;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.framework.github.GithubClient;
import com.mio.ai.framework.github.GithubRepoRef;
import com.mio.ai.framework.github.GithubUrlParser;
import com.mio.ai.resource.mapper.knowledge.DocumentMapper;
import com.mio.ai.resource.model.entity.Document;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.enums.DocumentStatusEnum;
import com.mio.ai.resource.model.vo.knowledge.GithubFileInfoVO;
import com.mio.ai.resource.model.vo.knowledge.GithubImportItemVO;
import com.mio.ai.resource.model.vo.knowledge.GithubPreviewVO;
import com.mio.ai.resource.service.knowledge.GithubImportService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.common.utils.R2Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
 * @description: GitHub 内容导入实现。安全约束：
 *               <ul>
 *                 <li>链接解析与下载目标均限定 GitHub 白名单主机（GithubUrlParser / GithubClient）</li>
 *                 <li>导入路径一律以服务端重新拉取的文件树为准，不信任客户端传回的大小与存在性</li>
                 <li>扩展名白名单与手动上传（FileType.KNOWLEDGE_FILE）一致，向量化读取共用同一套识别逻辑</li>
 *                 <li>文件数 / 单文件 / 总量三重上限 + 每用户 Redis 限流</li>
 *               </ul>
 */
@Slf4j
@Service
public class GithubImportServiceImpl implements GithubImportService {

    /** 与手动上传白名单保持一致，保证导入文件走同一条读取/识别管线 */
    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(FileType.KNOWLEDGE_FILE.getAllowedExtensions());

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 100L * 1024 * 1024;
    private static final int MAX_FILES_PER_IMPORT = 50;
    private static final int PREVIEW_LIST_LIMIT = 200;
    /** 分支名含斜杠（feature/x）时逐级尝试的最大层级 */
    private static final int MAX_REF_SEGMENTS = 3;

    private static final int PREVIEW_LIMIT_PER_HOUR = 60;
    private static final int IMPORT_LIMIT_PER_HOUR = 10;
    private static final DateTimeFormatter RATE_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @Resource
    private GithubClient githubClient;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private R2Util r2Util;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private record ResolvedRef(String branch, String subPath,
                               GithubClient.GithubTree tree) {
    }

    @Override
    public GithubPreviewVO preview(Long userId, String url) {
        checkRateLimit("preview", userId, PREVIEW_LIMIT_PER_HOUR);
        GithubRepoRef parsed = GithubUrlParser.parse(url);
        ResolvedRef resolved = resolveRef(parsed);

        long totalMatched = 0;
        long skippedUnsupported = 0;
        long skippedTooLarge = 0;
        List<GithubFileInfoVO> files = new ArrayList<>();
        for (GithubClient.GithubTreeEntry entry : resolved.tree().entries()) {
            if (!isBlobUnderSubPath(entry, resolved.subPath())) {
                continue;
            }
            if (!isSupportedExtension(entry.path())) {
                skippedUnsupported++;
                continue;
            }
            if (entry.size() > MAX_FILE_SIZE) {
                skippedTooLarge++;
                continue;
            }
            totalMatched++;
            if (files.size() < PREVIEW_LIST_LIMIT) {
                GithubFileInfoVO vo = new GithubFileInfoVO();
                vo.setPath(entry.path());
                vo.setSize(entry.size());
                files.add(vo);
            }
        }

        GithubPreviewVO vo = new GithubPreviewVO();
        vo.setOwner(parsed.owner());
        vo.setRepo(parsed.repo());
        vo.setBranch(resolved.branch());
        vo.setTotalMatched(totalMatched);
        vo.setTruncated(files.size() < totalMatched);
        vo.setSkippedUnsupported(skippedUnsupported);
        vo.setSkippedTooLarge(skippedTooLarge);
        vo.setFiles(files);
        return vo;
    }

    @Override
    public List<GithubImportItemVO> importToKnowledgeBase(Long kbId, Long userId, String url, List<String> paths) {
        checkRateLimit("import", userId, IMPORT_LIMIT_PER_HOUR);

        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }

        GithubRepoRef parsed = GithubUrlParser.parse(url);
        ResolvedRef resolved = resolveRef(parsed);
        if (resolved.tree().truncated()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仓库文件数过多，无法完整读取，请改用更精确的目录或文件链接");
        }

        // 以文件树为权威数据源逐个校验，路径不存在/超限/不支持按文件级错误返回
        Map<String, GithubClient.GithubTreeEntry> blobByPath = resolved.tree().entries().stream()
                .filter(e -> "blob".equals(e.type()))
                .collect(Collectors.toMap(GithubClient.GithubTreeEntry::path, e -> e,
                        (a, b) -> a, LinkedHashMap::new));

        List<String> distinctPaths = new ArrayList<>(new LinkedHashSet<>(paths));
        if (distinctPaths.size() > MAX_FILES_PER_IMPORT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单次最多导入 " + MAX_FILES_PER_IMPORT + " 个文件");
        }

        List<GithubClient.GithubTreeEntry> selected = new ArrayList<>();
        Map<String, String> pathErrors = new LinkedHashMap<>();
        long totalSize = 0;
        for (String path : distinctPaths) {
            GithubClient.GithubTreeEntry entry = blobByPath.get(path);
            if (entry == null || !isBlobUnderSubPath(entry, resolved.subPath())) {
                pathErrors.put(path, "文件不存在于该仓库目录中");
            } else if (!isSupportedExtension(path)) {
                pathErrors.put(path, "不支持的文件格式，允许: " + String.join(", ", SUPPORTED_EXTENSIONS));
            } else if (entry.size() > MAX_FILE_SIZE) {
                pathErrors.put(path, "文件超过 50MB 上限");
            } else {
                totalSize += entry.size();
                selected.add(entry);
            }
        }
        if (totalSize > MAX_TOTAL_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "所选文件总量超过 100MB，请减少文件后重试");
        }

        List<GithubImportItemVO> items = new ArrayList<>();
        for (Map.Entry<String, String> bad : pathErrors.entrySet()) {
            items.add(errorItem(bad.getKey(), bad.getValue()));
        }
        for (GithubClient.GithubTreeEntry entry : selected) {
            items.add(importSingleFile(kbId, parsed, resolved.branch(), entry));
        }

        long successCount = items.stream().filter(i -> "success".equals(i.getStatus())).count();
        log.info("GitHub 导入完成: kbId={}, userId={}, 成功 {}/{}", kbId, userId, successCount, items.size());
        return items;
    }

    private GithubImportItemVO importSingleFile(Long kbId, GithubRepoRef parsed, String branch,
                                                GithubClient.GithubTreeEntry entry) {
        GithubImportItemVO item = new GithubImportItemVO();
        item.setPath(entry.path());
        item.setFileSize(entry.size());
        try {
            byte[] bytes = githubClient.fetchRawFile(parsed.owner(), parsed.repo(), branch,
                    entry.path(), MAX_FILE_SIZE);
            if (bytes.length == 0) {
                return errorItem(entry.path(), "文件内容为空");
            }
            item.setFileSize(bytes.length);
            String fileUrl = r2Util.uploadFile(entry.path(), bytes, FileType.KNOWLEDGE_FILE, String.valueOf(kbId));

            Document document = new Document();
            document.setKbId(kbId);
            document.setFileName(entry.path());
            document.setFileType(extensionOf(entry.path()));
            document.setFileSize((long) bytes.length);
            document.setFilePath(fileUrl);
            document.setStatus(DocumentStatusEnum.PENDING.getCode());
            documentMapper.insert(document);

            item.setDocId(document.getId());
            item.setFileName(document.getFileName());
            item.setStatus("success");
        } catch (BusinessException e) {
            log.warn("GitHub 导入单文件失败: path={}, {}", entry.path(), e.getMessage());
            return errorItem(entry.path(), e.getMessage());
        } catch (Exception e) {
            log.error("GitHub 导入单文件异常: path={}", entry.path(), e);
            return errorItem(entry.path(), "导入失败，请稍后重试");
        }
        return item;
    }

    /**
     * 消解 ref 歧义：tree/blob 之后剩余段中，分支名可能占 1~3 段（feature/x 等），
     * 逐级用 git/trees 探测，探测成功即认为该段是分支，其余是子路径
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
                    boolean dirExists = tree.entries().stream().anyMatch(e -> isUnderSubPath(e.path(), subPath));
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

    private boolean isBlobUnderSubPath(GithubClient.GithubTreeEntry entry, String subPath) {
        if (!"blob".equals(entry.type())) {
            return false;
        }
        return isUnderSubPath(entry.path(), subPath);
    }

    /**
     * 子路径前缀匹配，要求目录边界对齐（"doc" 不能命中 "docs/..."）
     */
    private boolean isUnderSubPath(String path, String subPath) {
        if (subPath == null || subPath.isEmpty()) {
            return true;
        }
        return path.equals(subPath) || path.startsWith(subPath + "/");
    }

    private boolean isSupportedExtension(String path) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(path));
    }

    private String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private GithubImportItemVO errorItem(String path, String message) {
        GithubImportItemVO item = new GithubImportItemVO();
        item.setPath(path);
        item.setStatus("error");
        item.setError(message);
        return item;
    }

    /**
     * 每用户每小时固定窗口限流
     */
    private void checkRateLimit(String scene, Long userId, int limit) {
        String window = LocalDateTime.now().format(RATE_WINDOW);
        String key = "mio:github:" + scene + ":" + userId + ":" + window;
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
            log.warn("GitHub 导入限流检查异常，放行本次请求: {}", e.getMessage());
        }
    }
}
