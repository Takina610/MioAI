package com.mio.ai.resource.model.dto.agent;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

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
     * 图标配置JSON
     */
    @Size(max = 500, message = "图标配置长度不能超过500")
    private String icon;

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
     * 开场白（新会话首屏展示）
     */
    @Size(max = 1000, message = "开场白长度不能超过1000")
    @XssClean(mode = "rich")
    private String openingMessage;

    /**
     * 推荐问题（最多5条；字段级 @XssClean 逐项清理）
     */
    @Size(max = 5, message = "推荐问题最多5条")
    @XssClean(mode = "strict")
    private List<@Size(max = 100, message = "单条推荐问题长度不能超过100") String> openingQuestions;

    /**
     * 采样温度（null=引擎默认）
     */
    @DecimalMin(value = "0", message = "温度不能小于0")
    @DecimalMax(value = "2", message = "温度不能大于2")
    private Double temperature;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否公开
     */
    private Integer isPublic;
}
