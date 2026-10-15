package com.mio.ai.resource.model.dto.agent;

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

    /** 管理端多选状态过滤 */
    private java.util.List<Integer> statusList;

    /** 管理端多选类型过滤 */
    private java.util.List<Integer> typeList;

    /** 管理端按归属用户名模糊过滤 */
    private String ownerName;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 用户ID
     */
    private Long userId;
}
