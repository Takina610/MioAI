package com.mio.ai.framework.zagent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 引擎配置默认值回归：委派参数是新增能力，不能被既有四参构造悄悄降级。
 * <p>曾出现过两处易错语义：①四参构造应回落到安全默认（而非"不限"）；
 * ②显式传 0 应表示关闭限制，不能被 compact constructor 强行改回默认——
 * 否则运维无法通过配置关掉委派预算。
 */
class AgentEngineConfigDelegationDefaultsTest {

    @Test
    void fourArgConstructorFallsBackToSafeDelegationDefaults() {
        AgentEngineConfig config = new AgentEngineConfig(100, 1800, 3, 200_000);

        Assertions.assertEquals(AgentEngineConfig.DEFAULT_DELEGATION_TIMEOUT_MS,
                config.delegationTimeoutMs());
        Assertions.assertTrue(config.delegationTimeoutMs() > 0,
                "默认必须有预算，否则前台委派可无限挂起轮次线程");
        Assertions.assertEquals(AgentEngineConfig.DEFAULT_MAX_CONCURRENT_DELEGATIONS,
                config.maxConcurrentDelegations());
        Assertions.assertTrue(config.maxConcurrentDelegations() > 0,
                "默认必须有并发上限，否则模型一轮可拉起任意多子代理");
    }

    @Test
    void explicitZeroDisablesDelegationLimits() {
        AgentEngineConfig config = new AgentEngineConfig(100, 1800, 3, 200_000, 0, 0);

        Assertions.assertEquals(0, config.delegationTimeoutMs(),
                "显式 0 必须保留为不限时（运维关闭预算的出口）");
        Assertions.assertEquals(0, config.maxConcurrentDelegations(),
                "显式 0 必须保留为不限并发");
    }

    @Test
    void negativeValuesAreNormalizedToZero() {
        AgentEngineConfig config = new AgentEngineConfig(100, 1800, 3, 200_000, -5, -2);

        Assertions.assertEquals(0, config.delegationTimeoutMs());
        Assertions.assertEquals(0, config.maxConcurrentDelegations());
    }

    @Test
    void defaultsFactoryCarriesDelegationSettings() {
        AgentEngineConfig config = AgentEngineConfig.defaults();

        Assertions.assertEquals(AgentEngineConfig.DEFAULT_DELEGATION_TIMEOUT_MS,
                config.delegationTimeoutMs());
        Assertions.assertEquals(AgentEngineConfig.DEFAULT_MAX_CONCURRENT_DELEGATIONS,
                config.maxConcurrentDelegations());
        Assertions.assertTrue(config.delegationTimeoutMs() >= 60_000,
                "默认预算不应短于一次正常长任务");
    }

    @Test
    void customDelegationValuesArePreserved() {
        AgentEngineConfig config = new AgentEngineConfig(50, 900, 2, 100_000, 120_000, 8);

        Assertions.assertEquals(120_000, config.delegationTimeoutMs());
        Assertions.assertEquals(8, config.maxConcurrentDelegations());
    }
}
