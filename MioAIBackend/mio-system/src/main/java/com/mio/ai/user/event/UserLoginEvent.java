package com.mio.ai.user.event;

/**
 * 用户登录成功事件：登录发生在令牌签发之前，AOP 切面拿不到用户身份，
 * 由 mio-agent 监听本事件补记执行日志，避免 mio-system 反向依赖上层模块
 */
public record UserLoginEvent(Long userId, String userAccount) {
}
