package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建 MCP 工具请求：归属当前管理员，创建后为未激活态（校验通过才转正常）
 */
@Data
public class McpAdminCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "MCP 名称不能为空")
    @Size(max = 100, message = "MCP 名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    @Size(max = 2000, message = "MCP 描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;

    /** 连接配置 JSON */
    private String config;

    /** 工具清单 JSON */
    private String toolInfo;

    /** 0 私有 / 1 公开，缺省 0 */
    private Integer isPublic;
}
