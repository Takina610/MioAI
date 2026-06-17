package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新用户请求
 */
@Data
public class UserAdminUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 id
     */
    @NotNull(message = "用户 id 不能为空")
    private Long id;

    /**
     * 用户昵称
     */
    @Size(max = 64, message = "用户昵称长度不能超过 64")
    @XssClean(mode = "strict")
    private String userName;

    /**
     * 用户简介
     */
    @Size(max = 512, message = "用户简介长度不能超过 512")
    @XssClean(mode = "rich")
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    @Pattern(regexp = "^(user|admin)$", message = "用户角色只能是 user 或 admin")
    private String userRole;

    /**
     * 用户头像
     */
    @Size(max = 512, message = "头像 URL 长度不能超过 512")
    @XssClean(mode = "strict")
    private String userAvatar;
}
