package com.mio.ai.bot.service;

import com.mio.ai.framework.zagent.tools.SandboxFs;
import com.mio.ai.resource.model.entity.Skill;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能运行时：把绑定技能物化到沙箱 skills/ 目录，并生成注入上下文的技能清单段。
 *               对齐 zcode 的 Skills 语义——提示词只列名称/描述/路径，模型按需用 Read 读取 SKILL.md。
 */
@Slf4j
public final class SkillRuntime {

    /** 沙箱内技能根目录（工作区相对路径） */
    public static final String SKILLS_ROOT = "skills";

    /** 注入提示词的技能数量上限 */
    private static final int MAX_INJECTED_SKILLS = 20;
    /** 单条描述截断长度 */
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private SkillRuntime() {
    }

    /**
     * 物化技能文件到沙箱并返回注入用上下文段。
     *
     * @return 技能清单段；无技能或沙箱不可用时返回 null（本轮不注入）
     */
    public static String materialize(List<Skill> skills, SandboxFs fs) {
        if (skills == null || skills.isEmpty() || fs == null) {
            return null;
        }
        StringBuilder section = new StringBuilder();
        section.append("# Skills\n\n")
                .append("以下技能已安装到沙箱工作区。当任务与某技能相关时，先完整读取其 SKILL.md，再严格按其中的说明执行；")
                .append("技能目录内的脚本/参考文件按 SKILL.md 的指示使用：\n\n");

        int injected = 0;
        for (Skill skill : skills) {
            if (injected >= MAX_INJECTED_SKILLS) {
                log.warn("绑定技能超过 {} 个，仅物化前 {} 个", MAX_INJECTED_SKILLS, MAX_INJECTED_SKILLS);
                break;
            }
            String dir = SKILLS_ROOT + "/" + skill.getId();
            try {
                fs.write(dir + "/SKILL.md", skill.getContent() == null ? "" : skill.getContent());
                for (Map.Entry<String, String> file : parseFiles(skill.getFiles()).entrySet()) {
                    String safePath = sanitizeRelPath(file.getKey());
                    if (safePath == null || safePath.isEmpty()) {
                        continue;
                    }
                    fs.write(dir + "/" + safePath, file.getValue());
                }
            } catch (Exception e) {
                log.warn("技能物化失败，跳过: skillId={}, {}", skill.getId(), e.getMessage());
                continue;
            }
            String description = skill.getDescription() == null ? "" : skill.getDescription().strip();
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH) + "…";
            }
            section.append("- ").append(skill.getName()).append("（").append(dir)
                    .append("/SKILL.md）");
            if (!description.isEmpty()) {
                section.append("：").append(description);
            }
            section.append('\n');
            injected++;
        }
        return injected > 0 ? section.toString() : null;
    }

    /** files JSON（[{path, content}]）解析为有序 Map，坏记录忽略 */
    private static Map<String, String> parseFiles(String filesJson) {
        Map<String, String> result = new LinkedHashMap<>();
        if (filesJson == null || filesJson.isBlank()) {
            return result;
        }
        try {
            List<?> list = JacksonUtil.readListValue(filesJson, Object.class);
            if (list == null) {
                return result;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object path = map.get("path");
                    Object content = map.get("content");
                    if (path != null) {
                        result.put(String.valueOf(path), content == null ? "" : String.valueOf(content));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析技能附属文件失败: {}", e.getMessage());
        }
        return result;
    }

    /** 附属路径防御：拒绝绝对路径/穿越/反斜杠/控制字符，统一 '/' 分隔 */
    private static String sanitizeRelPath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..")
                || normalized.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            log.warn("忽略非法的技能附属路径: {}", path);
            return null;
        }
        return normalized;
    }
}
