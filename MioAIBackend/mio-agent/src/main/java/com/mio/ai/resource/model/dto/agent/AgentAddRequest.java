package com.mio.ai.resource.model.dto.agent;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体创建请求
 */
@Data
public class AgentAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 智能体名称
     */
    @NotBlank(message = "智能体名称不能为空")
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
     * 系统提示词
     */
    @Size(max = 10000, message = "系统提示词长度不能超过10000")
    @XssClean(mode = "rich")
    private String systemPrompt;
}
