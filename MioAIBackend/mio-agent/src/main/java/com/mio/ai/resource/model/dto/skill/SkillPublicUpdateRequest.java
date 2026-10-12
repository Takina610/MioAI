package com.mio.ai.resource.model.dto.skill;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 技能公开状态更新请求（仅自导入技能可设置）
 */
@Data
public class SkillPublicUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否公开（0-私有 1-公开）
     */
    @NotNull(message = "isPublic 不能为空")
    private Integer isPublic;
}
