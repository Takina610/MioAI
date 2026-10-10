package com.mio.ai.resource.service.skill;

import com.mio.ai.resource.model.vo.skill.SkillZipInstallVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能 zip 包安装服务
 */
public interface SkillZipInstallService {

    /**
     * 解压 zip 并安装其中所有含 SKILL.md 的技能目录（对齐 cc-switch 的 zip 安装语义）
     */
    SkillZipInstallVO installFromZip(Long userId, MultipartFile file);
}
