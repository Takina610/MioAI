package com.mio.ai.resource.listener;

import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.user.event.UserRegisteredEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 注册后初始化：为每个新用户创建专属 MioBot（type=3）。
 * 初始化失败只记日志不回滚注册——登录后的 /agents/my-mio-bot 调用会按需补建
 */
@Slf4j
@Component
public class UserRegisteredInitializer {

    @Resource
    private AgentService agentService;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            agentService.ensurePersonalMioBot(event.userId());
        } catch (Exception e) {
            log.error("为新用户 {} 初始化专属 MioBot 失败: {}", event.userId(), e.getMessage());
        }
    }
}
