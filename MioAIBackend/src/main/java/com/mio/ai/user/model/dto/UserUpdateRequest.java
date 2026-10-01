package com.mio.ai.user.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/3/28 14:34
 * @description: 更新用户请求
 */
@Data
public class UserUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @NotNull(message = "用户ID不能为空")
    private Long id;

    /**
     * 用户昵称
     */
    @Size(max = 50, message = "用户昵称长度不能超过50")
    @XssClean(mode = "strict")
    private String userName;

    /**
     * 简介
     */
    @Size(max = 500, message = "用户简介长度不能超过500")
    @XssClean(mode = "rich")
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    @Pattern(regexp = "^(user|admin)$", message = "用户角色只能是 user 或 admin")
    private String userRole;
}
