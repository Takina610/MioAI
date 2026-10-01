package com.mio.ai.customagent.model.dto.agent;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体更新请求
 */
@Data
public class AgentUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 智能体ID
     */
    @NotNull(message = "智能体ID不能为空")
    private Long id;

    /**
     * 智能体名称
     */
    @Size(max = 100, message = "智能体名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 智能体描述
     */
    @Size(max = 2000, message = "智能体描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL长度不能超过500")
    @XssClean(mode = "strict")
    private String avatar;

    /**
     * 智能体类型
     */
    private Integer type;

    /**
     * 系统提示词
     */
    @Size(max = 10000, message = "系统提示词长度不能超过10000")
    @XssClean(mode = "rich")
    private String systemPrompt;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否公开
     */
    private Integer isPublic;
}
