package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新 MCP 工具请求
 */
@Data
public class McpAdminUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * MCP 工具 id
     */
    @NotNull(message = "MCP 工具 id 不能为空")
    private Long id;

    /**
     * 工具名称
     */
    @Size(max = 100, message = "工具名称长度不能超过 100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 工具描述
     */
    @Size(max = 2000, message = "工具描述长度不能超过 2000")
    @XssClean(mode = "rich")
    private String description;

    /**
     * 状态（0-未激活 1-正常 2-异常）
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
