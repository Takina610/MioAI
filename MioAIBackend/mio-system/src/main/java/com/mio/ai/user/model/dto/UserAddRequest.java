package com.mio.ai.user.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/3/28 14:31
 * @description: 用户创建请求
 */
@Data
public class UserAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户昵称
     */
    @Size(max = 50, message = "用户昵称长度不能超过50")
    @XssClean(mode = "strict")
    private String userName;

    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 20, message = "账号长度必须在4-20之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字和下划线")
    @XssClean(mode = "escape")
    private String userAccount;

    /**
     * 用户头像
     */
    @Size(max = 500, message = "用户头像URL长度不能超过500")
    @XssClean(mode = "strict")
    private String userAvatar;

    /**
     * 用户简介
     */
    @Size(max = 500, message = "用户简介长度不能超过500")
    @XssClean(mode = "rich")
    private String userProfile;

    /**
     * 用户角色: user, admin
     */
    @Pattern(regexp = "^(user|admin)$", message = "用户角色只能是 user 或 admin")
    private String userRole;
}
