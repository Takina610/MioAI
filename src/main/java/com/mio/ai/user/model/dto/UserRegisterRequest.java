package com.mio.ai.user.model.dto;

import lombok.Data;

import java.io.Serializable;


/**
 * @author: Takina
 * @date: 2026/3/28 14:33
 * @description: 用户注册请求
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 8735650154179439661L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

}
