package com.mio.ai.framework.config;

import com.openai.core.Timeout;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 模型 HTTP 客户端定制（根源修复"长生成被掐断" + 网关必备头注入）。
 * <p>openai-java(okhttp) 默认 request(call) 超时 60s，覆盖"整次调用"——流式生成超 60s
 * 即被 cancel（StreamReset: CANCEL），前端表现为"连接中断，回答不完整"。
 * 属性链（spring.ai.openai.timeout / chat.timeout）实测未能稳定覆盖默认值，
 * 故经官方 customizer 扩展点直接钉死 okhttp 超时，与 SSE 会话超时对齐。
 * <p>interceptor 注入网关必需头（对 opencode zen 等 Cloudflare 前置网关）：
 * 浏览器 UA 与 x-opencode-session 路由头，多余头对其他兼容端点无害。
 */
@Configuration
public class OpenAiHttpClientConfig {

    /** opencode zen 网关的会话路由头（网关要求，任意稳定值即可） */
    private static final String ZEN_SESSION_HEADER = "x-opencode-session";

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";

    @Bean
    public OpenAiHttpClientBuilderCustomizer modelTimeoutCustomizer(
            @Value("${mio.ai.model.call-timeout-seconds:600}") long callTimeoutSeconds) {
        return builder -> {
            builder.timeout(Timeout.builder()
                    .request(Duration.ofSeconds(callTimeoutSeconds))
                    .read(Duration.ofSeconds(Math.min(callTimeoutSeconds, 300)))
                    .write(Duration.ofSeconds(120))
                    .connect(Duration.ofSeconds(15))
                    .build());
            builder.interceptor(chain -> chain.proceed(
                    chain.request().newBuilder()
                            .header("User-Agent", BROWSER_UA)
                            .header(ZEN_SESSION_HEADER, "mioai-agent")
                            .build()));
            org.slf4j.LoggerFactory.getLogger("MIO_MODEL_TIMEOUT")
                    .info("模型 okhttp 已定制: 超时 request={}s read={}s + 网关头",
                            callTimeoutSeconds, Math.min(callTimeoutSeconds, 300));
        };
    }
}
