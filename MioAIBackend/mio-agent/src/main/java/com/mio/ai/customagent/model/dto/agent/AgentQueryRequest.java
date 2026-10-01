package com.mio.ai.customagent.model.dto.agent;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentQueryRequest extends PageRequest implements Serializable {

    /**
     * 智能体名称（模糊查询）
     */
    private String name;

    /**
     * 智能体类型
     */
    private Integer type;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 用户ID
     */
    private Long userId;
}
