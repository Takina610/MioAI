package com.mio.ai.customagent.init;

import com.mio.ai.customagent.mapper.AgentMapper;
import com.mio.ai.customagent.model.entity.Agent;
import com.mio.ai.customagent.model.enums.AgentStatusEnum;
import com.mio.ai.customagent.model.enums.AgentTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BuiltInAgentInitializer {

    @Resource
    private AgentMapper agentMapper;

    public void run() {
        initCSApp();
        initMioManus();
    }

    private void initCSApp() {
        Agent existAgent = agentMapper.selectById(1L);
        if (existAgent != null) {
            log.info("CSApp智能体已存在，跳过初始化");
            return;
        }

        Agent csApp = new Agent();
        csApp.setId(1L);
        csApp.setUserId(null);
        csApp.setName("CS游戏助手");
        csApp.setDescription("CS游戏助手智能体，专注于CS游戏数据分析、战术指导、选手统计等功能。可以帮助玩家提升游戏技巧，了解游戏策略。");
        csApp.setAvatar("https://cdn.tak1na.cn/avatars/agent/1_1775132556128.png");
        csApp.setType(AgentTypeEnum.GENERAL.getCode());
        csApp.setStatus(AgentStatusEnum.PUBLISHED.getCode());
        csApp.setIsPublic(1);
        csApp.setUsageCount(0);
        csApp.setVersion("1.0.0");

        agentMapper.insert(csApp);
        log.info("CSApp智能体初始化完成");
    }

    private void initMioManus() {
        Agent existAgent = agentMapper.selectById(2L);
        if (existAgent != null) {
            log.info("MioManus智能体已存在，跳过初始化");
            return;
        }

        Agent mioManus = new Agent();
        mioManus.setId(2L);
        mioManus.setUserId(null);
        mioManus.setName("MioManus");
        mioManus.setDescription("全能型AI助手，致力于解决用户提出的任何任务。可以调用各种工具，高效完成复杂需求，拥有自主规划能力。");
        mioManus.setAvatar("https://cdn.tak1na.cn/avatars/agent/2_1775132471913.png");
        mioManus.setType(AgentTypeEnum.GENERAL.getCode());
        mioManus.setStatus(AgentStatusEnum.PUBLISHED.getCode());
        mioManus.setIsPublic(1);
        mioManus.setUsageCount(0);
        mioManus.setVersion("1.0.0");

        agentMapper.insert(mioManus);
        log.info("MioManus智能体初始化完成");
    }
}
