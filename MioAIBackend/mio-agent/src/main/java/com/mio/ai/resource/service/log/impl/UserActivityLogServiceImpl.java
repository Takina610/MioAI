package com.mio.ai.resource.service.log.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.resource.mapper.log.UserActivityLogMapper;
import com.mio.ai.resource.model.entity.UserActivityLog;
import com.mio.ai.resource.service.log.UserActivityLogService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户执行日志服务实现类：异步落库，业务请求零等待
 */
@Slf4j
@Service
public class UserActivityLogServiceImpl extends ServiceImpl<UserActivityLogMapper, UserActivityLog>
        implements UserActivityLogService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "user-activity-log");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void recordAsync(UserActivityLog row) {
        if (row == null) {
            return;
        }
        executor.execute(() -> {
            try {
                if (row.getCreateTime() == null) {
                    row.setCreateTime(new java.util.Date());
                }
                this.save(row);
            } catch (Exception e) {
                log.error("用户执行日志落库失败 module={} action={}", row.getModule(), row.getAction(), e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
