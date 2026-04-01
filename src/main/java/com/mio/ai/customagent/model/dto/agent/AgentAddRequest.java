package com.mio.ai.customagent.model.dto.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体创建请求
 */
@Data
public class AgentAddRequest implements Serializable {

    /**
     * 智能体名称
     */
    private String name;

    /**
     * 智能体描述
     */
    private String description;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 智能体类型
     */
    private Integer type;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 是否公开
     */
    private Integer isPublic;
}
