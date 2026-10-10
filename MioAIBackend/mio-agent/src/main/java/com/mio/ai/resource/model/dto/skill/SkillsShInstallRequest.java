package com.mio.ai.resource.model.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: skills.sh 一键安装请求（按仓库坐标定位技能）
 */
@Data
public class SkillsShInstallRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "owner 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "owner 不合法")
    private String owner;

    @NotBlank(message = "repo 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "repo 不合法")
    private String repo;

    @NotBlank(message = "skillId 不能为空")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "skillId 不合法")
    private String skillId;
}
