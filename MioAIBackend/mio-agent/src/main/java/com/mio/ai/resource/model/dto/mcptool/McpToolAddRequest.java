package com.mio.ai.resource.model.dto.mcptool;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具创建请求
 */
@Data
public class McpToolAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工具名称
     */
    @NotBlank(message = "工具名称不能为空")
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
    @NotBlank(message = "配置不能为空")
    @Size(max = 65536, message = "配置长度不能超过65536")
    private String config;

    /**
     * 工具信息：工具列表（JSON格式）
     */
    @Size(max = 200000, message = "工具信息长度不能超过200000")
    private String toolInfo;
}
