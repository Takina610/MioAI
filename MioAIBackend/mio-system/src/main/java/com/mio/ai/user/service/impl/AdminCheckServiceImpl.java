package com.mio.ai.user.service.impl;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.user.service.AdminCheckService;
import com.mio.ai.user.utils.RedisComponent;
import com.mio.ai.user.model.enums.UserRoleEnum;
import com.mio.ai.user.model.vo.LoginUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/3/28 16:50
 * @description:
 */

@Service
public class AdminCheckServiceImpl implements AdminCheckService {

    @Autowired
    RedisComponent redisComponent;

    @Override
    public void validateUser(String mustRole, String token) {
        // 获取当前登录用户
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 如果不需要权限，放行
        if (mustRoleEnum == null) {
            return;
        }

        // 根据获取用户信息
        LoginUserVO loginUser = redisComponent.getUserInfoByToken(token);

        if (null == loginUser || null == loginUser.getId()) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 以下的代码：必须有权限，才会通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
    }
}
