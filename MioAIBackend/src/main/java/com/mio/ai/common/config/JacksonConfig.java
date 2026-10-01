package com.mio.ai.common.config;

import com.mio.ai.common.jackson.XssCleanJacksonModule;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置类（Jackson 3 / Boot 4）
 * 注册 XSS 清理模块，对所有 @RequestBody 反序列化的字符串字段自动进行 XSS 过滤
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer xssCleanCustomizer() {
        return builder -> builder.addModule(new XssCleanJacksonModule());
    }
}
