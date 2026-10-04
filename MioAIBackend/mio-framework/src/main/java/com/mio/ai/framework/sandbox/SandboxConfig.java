package com.mio.ai.framework.sandbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 沙箱装配：enabled 时提供共享会话（长连接复用），所有沙箱原语工具共用。
 */
@Configuration
@ConditionalOnProperty(prefix = "mio.ai.sandbox", name = "enabled", havingValue = "true")
public class SandboxConfig {

    @Bean
    public SandboxSession sandboxSession(SandboxProperties properties) {
        return new SandboxSession(properties);
    }
}
