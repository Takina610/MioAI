package com.mio.ai.user.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/3/28 14:34
 * @description: 更新用户请求
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}
