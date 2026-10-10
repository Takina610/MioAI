package com.mio.ai.resource.model.dto.skill;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 技能导入请求
 */
@Data
public class SkillGithubImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * GitHub 仓库 / 目录链接（与预览时一致）
     */
    @NotBlank(message = "请输入 GitHub 链接")
    @Size(max = 2000, message = "链接过长")
    @XssClean(mode = "strict")
    private String url;

    /**
     * 待导入技能的 SKILL.md 路径列表（来自预览结果）
     */
    @NotEmpty(message = "请选择要导入的技能")
    @Size(max = 20, message = "单次最多导入 20 个技能")
    private List<String> skillPaths;
}
