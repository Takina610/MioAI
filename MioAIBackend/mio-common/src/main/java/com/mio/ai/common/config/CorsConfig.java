package com.mio.ai.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author: Takina
 * @date: 2026/3/28 11:23
 * @description: 全局跨域配置
 * <p>本项目登录态走请求头 token（localStorage），不使用 Cookie，
 * 因此默认不开启 allowCredentials——「任意来源 + 携带凭证」是最危险的组合，
 * 一旦后续引入 Cookie 会话就会直接变成跨站越权。确需跨域携带 Cookie 时显式开启。
 */

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许的来源模式，逗号分隔；默认全放行（同源部署下不产生跨域请求） */
    @Value("${mio.ai.cors.allowed-origin-patterns:*}")
    private String allowedOriginPatterns;

    /** 是否允许携带 Cookie；默认关闭 */
    @Value("${mio.ai.cors.allow-credentials:false}")
    private boolean allowCredentials;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] patterns = allowedOriginPatterns.split(",");
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = patterns[i].trim();
        }
        registry.addMapping("/**")
                .allowCredentials(allowCredentials)
                .allowedOriginPatterns(patterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
