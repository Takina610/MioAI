package com.mio.ai.resource.model.dto.agent;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 内置智能体用户级个性化配置请求（字段覆盖语义：null=清除该项覆盖，回退内置默认）
 */
@Data
public class AgentUserConfigRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 人设提示词覆盖
     */
    @Size(max = 10000, message = "系统提示词长度不能超过10000")
    @XssClean(mode = "rich")
    private String systemPrompt;

    /**
     * 图标配置覆盖JSON
     */
    @Size(max = 500, message = "图标配置长度不能超过500")
    private String icon;

    /**
     * 是否发布到广场（null=保持不变）
     */
    private Integer isPublic;
}
