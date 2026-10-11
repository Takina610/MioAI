package com.mio.ai.bot.config;

import com.mio.ai.framework.zagent.task.BackgroundTasks;
import com.mio.ai.resource.mapper.task.AgentBackgroundTaskMapper;
import com.mio.ai.resource.service.task.BackgroundTaskStoreImpl;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 任务引擎持久化装配：启动时把 MySQL 仓储挂进 zagent 注册表并做重启对账
 * （进程内任务改判 interrupted、注册序号续接、沙箱脱离任务保持 running 待探针重连）。
 */
@Slf4j
@Configuration
public class BackgroundTaskPersistenceConfig {

    @Resource
    private AgentBackgroundTaskMapper mapper;

    @PostConstruct
    public void installStore() {
        BackgroundTasks.setStore(new BackgroundTaskStoreImpl(mapper));
    }

    @Bean
    public ApplicationRunner backgroundTaskReconciler() {
        return args -> {
            BackgroundTasks.instance().reconcileOnStartup();
            log.info("后台任务注册表对账完成（持久化仓储已挂载）");
        };
    }
}
