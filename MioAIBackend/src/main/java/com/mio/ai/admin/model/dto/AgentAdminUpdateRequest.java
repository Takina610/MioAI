package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新智能体请求
 */
@Data
public class AgentAdminUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 智能体 id
     */
    @NotNull(message = "智能体 id 不能为空")
    private Long id;

    /**
     * 智能体名称
     */
    @Size(max = 100, message = "智能体名称长度不能超过 100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 智能体描述
     */
    @Size(max = 2000, message = "智能体描述长度不能超过 2000")
    @XssClean(mode = "rich")
    private String description;

    /**
     * 系统提示词
     */
    @Size(max = 10000, message = "系统提示词长度不能超过 10000")
    @XssClean(mode = "rich")
    private String systemPrompt;

    /**
     * 状态（0-草稿 1-已发布 2-已禁用）
     * 草稿可变为已发布或禁用，已发布/禁用不可变为草稿
     */
    @Min(value = 0, message = "状态值不合法")
    @Max(value = 2, message = "状态值不合法")
    private Integer status;

    /**
     * 是否公开（0-私有 1-公开）
     */
    @Min(value = 0, message = "公开状态值不合法")
    @Max(value = 1, message = "公开状态值不合法")
    private Integer isPublic;
}
