package com.mio.ai.customagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体视图对象
 */
@Data
public class AgentVO implements Serializable {

    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

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
     * 智能体类型描述
     */
    private String typeDesc;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 版本号
     */
    private String version;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
