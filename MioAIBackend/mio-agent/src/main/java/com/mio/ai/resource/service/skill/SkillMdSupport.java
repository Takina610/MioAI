package com.mio.ai.resource.service.skill;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: SKILL.md 解析与技能文件约束的共享工具。
 *               发现规则：目录内含 SKILL.md 即视为一个技能；frontmatter 的
 *               name/description 优先，缺省由调用方用目录名兜底。
 *               GitHub 导入与 zip 安装共用同一套白名单和上限。
 */
public final class SkillMdSupport {

    /** 附属文件扩展名白名单（技能常含脚本/参考文档/模板） */
    public static final Set<String> FILE_EXTENSIONS = Set.of(
            "md", "markdown", "txt", "py", "sh", "js", "ts", "mjs", "cjs", "json",
            "yaml", "yml", "toml", "csv", "tsv", "html", "htm", "css", "xml", "sql",
            "rb", "go", "rs", "java", "kt", "c", "cpp", "h", "hpp", "cs", "php",
            "swift", "bat", "ps1", "ini", "cfg", "conf", "properties", "proto");

    public static final Set<String> FILE_NAMES = Set.of(
            "dockerfile", "makefile", "license", ".gitignore", ".env.example");

    public static final long MAX_FILE_SIZE = 200L * 1024;
    public static final long MAX_SKILL_TOTAL_SIZE = 2L * 1024 * 1024;
    public static final int MAX_FILES_PER_SKILL = 30;
    /** 单个 zip / 一次导入可安装的技能数上限 */
    public static final int MAX_SKILLS_PER_BATCH = 50;

    /** zip 附件整体上限（解压预算由单技能上限兜底） */
    public static final long MAX_ZIP_SIZE = 30L * 1024 * 1024;

    private SkillMdSupport() {
    }

    /**
     * 是否为技能标记文件：SKILL.md（不区分大小写）
     */
    public static boolean isSkillMarker(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return "skill.md".equalsIgnoreCase(fileName);
    }

    /**
     * 附属文件是否在收录范围：扩展名白名单或特殊文件名
     */
    public static boolean isSupportedFile(String path) {
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
     * 解析 SKILL.md frontmatter（--- 包裹区块），key 统一小写，只保留文本值。
     * 支持带引号值、YAML 块标量（| >）与多行折叠；解析失败静默返回空映射，由调用方用目录名兜底。
     */
    public static Map<String, String> parseFrontmatter(String content) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (content == null) {
            return result;
        }
        String normalized = content.stripLeading().replace("\uFEFF", "");
        String[] lines = normalized.split("\n", -1);
        if (lines.length < 2 || !"---".equals(lines[0].trim())) {
            return result;
        }
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.equals("---") || trimmed.equals("...")) {
                break;
            }
            int colon = trimmed.indexOf(':');
            if (colon > 0 && !Character.isWhitespace(line.charAt(0))) {
                flush(result, currentKey, currentValue);
                currentKey = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String rawValue = trimmed.substring(colon + 1).trim();
                // 块标量指示符（| > 及其修饰）不是值本身，实际内容是后续缩进行
                if (!rawValue.matches("[|>][+-]?")) {
                    currentValue.append(rawValue);
                }
            } else if (currentKey != null && !trimmed.isEmpty()) {
                // 多行值：追加并以空格连接（description 是单行字段，折叠即可）
                currentValue.append(' ').append(trimmed);
            }
        }
        flush(result, currentKey, currentValue);
        return result;
    }

    private static void flush(Map<String, String> result, String key, StringBuilder value) {
        if (key == null) {
            return;
        }
        String v = value.toString().trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1).trim();
        } else if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
            v = v.substring(1, v.length() - 1).trim();
        }
        if (!v.isEmpty()) {
            result.put(key, v);
        }
        value.setLength(0);
    }

    /**
     * 从 frontmatter 结果中取描述，限制长度（卡片/清单展示用）
     */
    public static String descriptionOf(Map<String, String> frontmatter, int maxLen) {
        String desc = frontmatter.get("description");
        if (desc == null) {
            return null;
        }
        return desc.length() > maxLen ? desc.substring(0, maxLen) : desc;
    }

    /**
     * 截断到数据库列长：超长直接入库会以数据溢出异常的形式变成"系统错误"
     */
    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * 仓库内 SKILL.md 的跳转链接（blob 页）
     */
    public static String buildDocUrl(String owner, String repo, String branch, String skillPath) {
        String pathInRepo = (skillPath == null || skillPath.isEmpty())
                ? "SKILL.md"
                : skillPath + "/SKILL.md";
        return truncate("https://github.com/" + owner + "/" + repo + "/blob/" + branch + "/" + pathInRepo, 500);
    }
}
