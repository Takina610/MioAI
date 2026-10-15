package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建用户请求：走注册链路（校验账号唯一/密码强度），再补全资料字段
 */
@Data
public class UserAdminCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户账号不能为空")
    @Size(max = 40, message = "用户账号长度不能超过40")
    private String userAccount;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度至少 8 位")
    private String userPassword;

    @Size(max = 40, message = "用户昵称长度不能超过40")
    @XssClean(mode = "strict")
    private String userName;

    @Size(max = 500, message = "用户简介长度不能超过500")
    @XssClean(mode = "rich")
    private String userProfile;

    /** user / admin，缺省 user */
    private String userRole;

    @Size(max = 500, message = "头像URL长度不能超过500")
    private String userAvatar;
}
