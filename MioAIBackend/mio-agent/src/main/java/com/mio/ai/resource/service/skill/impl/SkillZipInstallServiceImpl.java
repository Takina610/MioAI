package com.mio.ai.resource.service.skill.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.resource.mapper.skill.SkillMapper;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.resource.model.enums.SkillStatusEnum;
import com.mio.ai.resource.model.vo.skill.SkillZipInstallVO;
import com.mio.ai.resource.service.skill.SkillMdSupport;
import com.mio.ai.resource.service.skill.SkillRateLimiter;
import com.mio.ai.resource.service.skill.SkillZipInstallService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: zip 安装实现（对齐 cc-switch）：解压后识别所有含 SKILL.md 的目录为一个技能，
 *               安装名 = frontmatter.name → 目录名 → zip 文件名，同名跳过。
 *               安全约束：zip-slip 路径校验、条目数/单文件/单技能总量上限。
 */
@Slf4j
@Service
public class SkillZipInstallServiceImpl implements SkillZipInstallService {

    private static final int MAX_ENTRIES = 5000;
    /** 压缩包内条目总数上限（含被过滤的文件，防海量条目拖垮解析） */
    private static final int MAX_ENTRIES_SEEN = 20000;
    /** 压缩包内单文件读取上限（超限文件即使扩展名合法也不收） */
    private static final int MAX_ENTRY_READ = 512 * 1024;
    /** 解压后总字节预算：30MB 压缩包理论上可膨胀到 GB 级（压缩炸弹），超预算立即中止 */
    private static final long MAX_DECOMPRESSED_TOTAL = 512L * 1024 * 1024;
    /** zip 安装限流：次/小时/用户 */
    private static final int ZIP_INSTALL_LIMIT_PER_HOUR = 20;

    @Resource
    private SkillMapper skillMapper;

    @Resource
    private SkillRateLimiter skillRateLimiter;

    /** 一个技能目录：仓库内相对路径 + SKILL.md 路径 + 附属文件（相对路径 -> 内容） */
    private record SkillDir(String dir, String skillMd, Map<String, String> files) {
    }

    @Override
    public SkillZipInstallVO installFromZip(Long userId, MultipartFile file) {
        skillRateLimiter.check("zip", userId, ZIP_INSTALL_LIMIT_PER_HOUR);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择 zip 文件");
        }
        if (file.getSize() > SkillMdSupport.MAX_ZIP_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "zip 文件超过大小上限（30MB）");
        }
        String zipName = StrUtil.blankToDefault(file.getOriginalFilename(), "skill.zip");
        if (!zipName.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 .zip 文件");
        }

        Map<String, byte[]> entries = readEntries(file);
        List<SkillDir> skillDirs = collectSkillDirs(entries);
        String zipStem = zipStem(zipName);

        SkillZipInstallVO vo = new SkillZipInstallVO();
        List<SkillZipInstallVO.SkippedItem> skipped = new ArrayList<>();
        vo.setSkipped(skipped);
        Set<String> namesInZip = new HashSet<>();
        int installed = 0;

        for (SkillDir skillDir : skillDirs) {
            byte[] markerBytes = entries.get(skillDir.skillMd());
            Map<String, String> frontmatter = SkillMdSupport.parseFrontmatter(
                    new String(markerBytes, StandardCharsets.UTF_8));
            String name = resolveName(frontmatter, skillDir.dir(), zipStem);

            if (!namesInZip.add(name)) {
                skipped.add(skippedItem(name, "压缩包内存在同名技能"));
                continue;
            }
            if (existsName(userId, name)) {
                skipped.add(skippedItem(name, "同名技能已存在"));
                continue;
            }

            Skill skill = new Skill();
            skill.setUserId(userId);
            skill.setName(SkillMdSupport.cleanName(name, 100));
            skill.setDescription(SkillMdSupport.cleanDescription(frontmatter, 500));
            skill.setContent(new String(markerBytes, StandardCharsets.UTF_8));
            skill.setFiles(skillDir.files().isEmpty() ? null : JacksonUtil.writeValueAsString(toFileList(skillDir.files())));
            skill.setStatus(SkillStatusEnum.ACTIVE.getCode());
            skill.setInstalled(1);
            skillMapper.insert(skill);
            installed++;
        }
        vo.setInstalledCount(installed);
        log.info("zip 安装完成: userId={}, file={}, 成功 {}, 跳过 {}", userId, zipName, installed, skipped.size());
        return vo;
    }

    /**
     * 读取 zip 条目。文件名编码优先按 UTF-8；Windows 中文系统压缩的 zip 文件名是 GBK
     * 且不带 UTF-8 标志位，强解会抛 MALFORMED，此时用 GB18030 重读一次
     */
    private Map<String, byte[]> readEntries(MultipartFile file) {
        try {
            return readEntries(file, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            try {
                return readEntries(file, Charset.forName("GB18030"));
            } catch (IOException io) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "读取 zip 文件失败: " + io.getMessage());
            } catch (IllegalArgumentException e2) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法读取压缩包内的文件名，zip 文件可能已损坏");
            }
        } catch (ZipException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包无法解析：可能已加密、已损坏或不是标准 zip 格式");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "读取 zip 文件失败: " + e.getMessage());
        }
    }

    private Map<String, byte[]> readEntries(MultipartFile file, Charset charset) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        long decompressedTotal = 0;
        int entriesSeen = 0;
        try (InputStream in = file.getInputStream();
             ZipInputStream zip = new ZipInputStream(in, charset)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entriesSeen > MAX_ENTRIES_SEEN) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包内文件数过多");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalizeEntryPath(entry.getName());
                if (!path.isEmpty() && (SkillMdSupport.isSkillMarker(path) || SkillMdSupport.isSupportedFile(path))) {
                    if (entries.size() >= MAX_ENTRIES) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包内文件数过多");
                    }
                    // 超限文件读满即丢，保证流正常推进
                    byte[] bytes = zip.readNBytes(MAX_ENTRY_READ + 1);
                    decompressedTotal += bytes.length;
                    if (bytes.length <= SkillMdSupport.MAX_FILE_SIZE) {
                        entries.put(path, bytes);
                    }
                }
                // 显式排干当前条目剩余字节并计数：留给 getNextEntry 的隐式跳过无法统计，压缩炸弹会从这里漏过去
                decompressedTotal += drainRemainder(zip);
                if (decompressedTotal > MAX_DECOMPRESSED_TOTAL) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包解压后体积过大，已中止处理");
                }
            }
        }
        return entries;
    }

    /** 排干当前条目剩余解压字节（entry 边界处 read 返回 -1），返回读取量 */
    private long drainRemainder(ZipInputStream zip) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = zip.read(buf)) != -1) {
            total += n;
        }
        return total;
    }

    /**
     * 找出所有含 SKILL.md 的技能目录并收集目录内附属文件。
     * 已被外层技能包含的嵌套标记跳过（与 cc-switch 一致：命中技能后不再下钻）
     */
    private List<SkillDir> collectSkillDirs(Map<String, byte[]> entries) {
        List<String> markers = entries.keySet().stream()
                .filter(SkillMdSupport::isSkillMarker)
                .sorted()
                .toList();
        if (markers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包内未找到技能（未发现 SKILL.md）");
        }
        if (markers.size() > SkillMdSupport.MAX_SKILLS_PER_BATCH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "压缩包内技能数超过上限（" + SkillMdSupport.MAX_SKILLS_PER_BATCH + " 个）");
        }

        List<SkillDir> result = new ArrayList<>();
        for (String marker : markers) {
            String dir = dirOf(marker);
            if (result.stream().anyMatch(existing -> isUnderDir(dir, existing.dir()))) {
                continue;
            }
            Map<String, String> files = new LinkedHashMap<>();
            long totalSize = marker.length();
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String path = e.getKey();
                if (path.equals(marker) || !isUnderDir(path, dir) || !SkillMdSupport.isSupportedFile(path)) {
                    continue;
                }
                if (files.size() >= SkillMdSupport.MAX_FILES_PER_SKILL
                        || totalSize + e.getValue().length > SkillMdSupport.MAX_SKILL_TOTAL_SIZE) {
                    log.warn("技能附属文件超出上限，截断: zip 内 {}", marker);
                    break;
                }
                totalSize += e.getValue().length;
                files.put(relativePath(path, dir), new String(e.getValue(), StandardCharsets.UTF_8));
            }
            result.add(new SkillDir(dir, marker, files));
        }
        return result;
    }

    /** 安装名：frontmatter.name → 目录名 → zip 文件名 */
    private String resolveName(Map<String, String> frontmatter, String dir, String zipStem) {
        String name = StrUtil.blankToDefault(frontmatter.get("name"), "");
        if (name.isEmpty()) {
            name = baseName(dir);
        }
        if (name.isEmpty()) {
            name = zipStem;
        }
        return name;
    }

    /** 附属文件统一存为 [{path, content}] 列表（与 GitHub 导入一致） */
    private List<Map<String, String>> toFileList(Map<String, String> files) {
        List<Map<String, String>> list = new ArrayList<>();
        files.forEach((path, content) -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("path", path);
            item.put("content", content);
            list.add(item);
        });
        return list;
    }

    private boolean existsName(Long userId, String name) {
        return skillMapper.selectCount(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getUserId, userId)
                .eq(Skill::getName, name)) > 0;
    }

    private SkillZipInstallVO.SkippedItem skippedItem(String name, String reason) {
        SkillZipInstallVO.SkippedItem item = new SkillZipInstallVO.SkippedItem();
        item.setName(name);
        item.setReason(reason);
        return item;
    }

    /** 归一化条目路径：统一分隔符，拒绝绝对路径、盘符与穿越（zip-slip） */
    private String normalizeEntryPath(String raw) {
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || path.contains(":")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包包含非法路径: " + raw);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "压缩包包含非法路径: " + raw);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    /** 目录边界对齐的前缀匹配：path 是否位于 dir 之下（dir 为空表示根） */
    private boolean isUnderDir(String path, String dir) {
        return dir == null || dir.isEmpty() || path.startsWith(dir + "/");
    }

    private String dirOf(String markerPath) {
        int slash = markerPath.lastIndexOf('/');
        return slash < 0 ? "" : markerPath.substring(0, slash);
    }

    private String relativePath(String path, String dir) {
        return dir.isEmpty() ? path : path.substring(dir.length() + 1);
    }

    private String baseName(String dir) {
        return dir == null || dir.isEmpty() ? "" : dir.substring(dir.lastIndexOf('/') + 1);
    }

    private String zipStem(String zipName) {
        String name = zipName;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
