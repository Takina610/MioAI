package com.mio.ai.resource.model.dto.agent;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建群聊智能体请求：群本体只有名称/描述，能力来自成员智能体集合
 */
@Data
public class AgentGroupCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "群聊名称不能为空")
    @Size(max = 32, message = "群聊名称长度不能超过32")
    @XssClean(mode = "strict")
    private String name;

    @Size(max = 200, message = "群聊描述长度不能超过200")
    @XssClean(mode = "rich")
    private String description;

    /** 成员智能体ID（须为当前用户自己的自定义智能体，至少 2 个） */
    @NotEmpty(message = "请选择群聊成员")
    private List<Long> memberAgentIds;
}
