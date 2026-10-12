package com.mio.ai.resource.model.vo.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 内置智能体用户级个性化配置视图
 */
@Data
public class AgentUserConfigVO implements Serializable {

    private Long agentId;

    /**
     * 人设提示词覆盖
     */
    private String systemPrompt;

    /**
     * 开场白覆盖
     */
    private String openingMessage;

    /**
     * 推荐问题覆盖
     */
    private List<String> openingQuestions;

    /**
     * 采样温度覆盖（null=跟随内置默认）
     */
    private Double temperature;

    /**
     * 图标配置覆盖JSON
     */
    private String icon;

    /**
     * 是否发布到广场（0-未发布 1-已发布）
     */
    private Integer isPublic;
}
