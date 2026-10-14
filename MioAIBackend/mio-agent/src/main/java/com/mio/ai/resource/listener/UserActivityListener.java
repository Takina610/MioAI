package com.mio.ai.resource.listener;

import com.mio.ai.resource.model.entity.UserActivityLog;
import com.mio.ai.resource.service.log.UserActivityLogService;
import com.mio.ai.user.event.UserLoginEvent;
import com.mio.ai.user.event.UserRegisteredEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 账号生命周期执行日志：登录/注册发生在令牌可用之前（AOP 拿不到身份），
 * 由事件补记——执行履历因此覆盖账号从注册起的全部动作。
 */
@Slf4j
@Component
public class UserActivityListener {

    @Resource
    private UserActivityLogService userActivityLogService;

    @EventListener
    public void onUserLogin(UserLoginEvent event) {
        record(event.userId(), "auth", "login", event.userAccount());
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        record(event.userId(), "auth", "register", null);
    }

    private void record(Long userId, String module, String action, String summary) {
        try {
            UserActivityLog row = new UserActivityLog();
            row.setUserId(userId);
            row.setModule(module);
            row.setAction(action);
            row.setSummary(summary);
            row.setSuccess(1);
            row.setCreateTime(new Date());
            userActivityLogService.recordAsync(row);
        } catch (Exception e) {
            log.debug("账号生命周期日志记录失败: {}", e.getMessage());
        }
    }
}
