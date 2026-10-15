package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建智能体请求：归属当前管理员，类型/状态/公开可选（缺省 自定义/草稿/私有）
 */
@Data
public class AgentAdminCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 100, message = "智能体名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    @Size(max = 2000, message = "智能体描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;

    @Size(max = 500, message = "头像URL长度不能超过500")
    private String avatar;

    @Size(max = 500, message = "图标配置长度不能超过500")
    private String icon;

    @Size(max = 10000, message = "系统提示词长度不能超过10000")
    private String systemPrompt;

    /** 1 智能体 / 2 群聊，缺省 1 */
    private Integer type;

    /** 0 草稿 / 1 已发布 / 2 禁用，缺省 0 */
    private Integer status;

    /** 0 私有 / 1 公开，缺省 0 */
    private Integer isPublic;
}
