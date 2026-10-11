package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 管理员更新技能请求
 */
@Data
public class SkillAdminUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "技能 id 不能为空")
    private Long id;

    @XssClean(mode = "strict")
    private String name;

    @XssClean(mode = "strict")
    private String description;

    /**
     * 状态（0-禁用 1-正常）
     */
    private Integer status;
}
