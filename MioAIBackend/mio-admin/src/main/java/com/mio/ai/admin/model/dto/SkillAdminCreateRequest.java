package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建技能请求：本地创建 SKILL.md 技能壳（区别于 GitHub/zip 安装来源）
 */
@Data
public class SkillAdminCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 100, message = "技能名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    @Size(max = 2000, message = "技能描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;

    /** SKILL.md 正文 */
    private String content;

    private Integer isPublic;

    /** 1 正常 / 0 禁用，缺省 1 */
    private Integer status;
}
