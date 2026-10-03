package com.mio.ai.resource.model.dto.mcptool;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具更新请求
 */
@Data
public class McpToolUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工具ID
     */
    @NotNull(message = "工具ID不能为空")
    private Long id;

    /**
     * 工具名称
     */
    @Size(max = 100, message = "工具名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 工具描述
     */
    @Size(max = 500, message = "工具描述长度不能超过500")
    @XssClean(mode = "rich")
    private String description;

    /**
     * 配置（JSON格式）
     */
    @Size(max = 65536, message = "配置长度不能超过65536")
    private String config;

    /**
     * 工具信息：工具列表（JSON格式）
     */
    @Size(max = 200000, message = "工具信息长度不能超过200000")
    private String toolInfo;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否公开
     */
    private Integer isPublic;
}
