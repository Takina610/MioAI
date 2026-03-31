package com.mio.ai.user.service;

/**
 * @author: Takina
 * @date: 2026/3/28 16:49
 * @description:
 */
public interface AdminCheckService {
    /**
     * 校验用户是否为管理员
     * @param mustRole
     * @param token
     */
    void validateUser(String mustRole, String token);
}
