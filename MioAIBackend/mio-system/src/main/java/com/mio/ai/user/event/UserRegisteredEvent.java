package com.mio.ai.user.event;

/**
 * 用户注册成功事件：跨模块的注册后初始化（如 mio-agent 为新用户创建专属 MioBot）
 * 通过监听本事件完成，避免 mio-system 反向依赖上层模块
 */
public record UserRegisteredEvent(Long userId) {
}
