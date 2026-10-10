package com.mio.ai.resource.model.dto.agentskill;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 智能体绑定技能请求
 */
@Data
public class AgentSkillAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotNull(message = "技能ID不能为空")
    private Long skillId;

    /**
     * 是否启用（0-禁用 1-启用）
     */
    private Integer enabled;
}
