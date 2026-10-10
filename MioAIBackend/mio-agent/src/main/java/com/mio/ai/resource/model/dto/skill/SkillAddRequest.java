package com.mio.ai.resource.model.dto.skill;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能创建请求
 */
@Data
public class SkillAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 技能名称
     */
    @NotBlank(message = "技能名称不能为空")
    @Size(max = 100, message = "技能名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 技能描述（供智能体判断何时使用）
     */
    @Size(max = 500, message = "技能描述长度不能超过500")
    @XssClean(mode = "strict")
    private String description;

    /**
     * SKILL.md 内容
     */
    @Size(max = 200_000, message = "技能内容过大")
    private String content;

    /**
     * 附属文件
     */
    @Valid
    @Size(max = 30, message = "附属文件最多 30 个")
    private List<SkillFileDto> files;

    /**
     * 是否公开（0-私有 1-公开）
     */
    private Integer isPublic;

    /**
     * 来源链接（GitHub 导入时记录）
     */
    @Size(max = 500, message = "来源链接过长")
    private String sourceUrl;
}
