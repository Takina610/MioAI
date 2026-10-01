package com.mio.ai.user.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/2
 * @description: 用户密码修改请求
 */
@Data
public class UserPasswordUpdateRequest implements Serializable {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认密码
     */
    private String confirmPassword;

    private static final long serialVersionUID = 1L;
}
