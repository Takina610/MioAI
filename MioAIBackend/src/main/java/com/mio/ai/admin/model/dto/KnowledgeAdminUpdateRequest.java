package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新知识库请求
 */
@Data
public class KnowledgeAdminUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识库 id
     */
    @NotNull(message = "知识库 id 不能为空")
    private Long id;

    /**
     * 知识库名称
     */
    @Size(max = 100, message = "知识库名称长度不能超过 100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 知识库描述
     */
    @Size(max = 2000, message = "知识库描述长度不能超过 2000")
    @XssClean(mode = "rich")
    private String description;

    /**
     * 状态（0-创建中 1-正常 2-已禁用）
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
