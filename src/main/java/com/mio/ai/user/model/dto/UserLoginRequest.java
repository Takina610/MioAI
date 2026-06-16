package com.mio.ai.user.model.dto;

import com.mio.ai.common.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/3/28 14:32
 * @description: 用户登录请求
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 8735650154179439661L;

    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    @Size(max = 20, message = "账号长度不能超过20")
    @XssClean(mode = "escape")
    private String userAccount;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 32, message = "密码长度不能超过32")
    private String userPassword;
}
