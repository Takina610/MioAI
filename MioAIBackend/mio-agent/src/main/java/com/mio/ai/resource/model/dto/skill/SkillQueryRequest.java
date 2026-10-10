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

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 用户ID（服务端强制以登录态覆盖）
     */
    private Long userId;
}
