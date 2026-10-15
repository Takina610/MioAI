package com.mio.ai.resource.model.dto.skill;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 名称（模糊搜索）
     */
    private String name;

    /**
     * 状态
     */
    private Integer status;

    /** 管理端多选状态过滤 */
    private java.util.List<Integer> statusList;

    /** 管理端按归属用户名模糊过滤 */
    private String ownerName;

    /**
     * 用户ID（服务端强制以登录态覆盖）
     */
    private Long userId;
}
