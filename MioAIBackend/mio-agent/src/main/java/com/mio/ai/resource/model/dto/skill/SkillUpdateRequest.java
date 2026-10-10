package com.mio.ai.resource.model.dto.skill;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能更新请求
 */
@Data
public class SkillUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "技能ID不能为空")
    private Long id;

    @Size(max = 100, message = "技能名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    @Size(max = 500, message = "技能描述长度不能超过500")
    @XssClean(mode = "strict")
    private String description;

    @Size(max = 200_000, message = "技能内容过大")
    private String content;

    @Valid
    @Size(max = 30, message = "附属文件最多 30 个")
    private List<SkillFileDto> files;

    /**
     * 状态（0-禁用 1-正常）
     */
    private Integer status;

    /**
     * 是否公开（0-私有 1-公开）
     */
    private Integer isPublic;
}
