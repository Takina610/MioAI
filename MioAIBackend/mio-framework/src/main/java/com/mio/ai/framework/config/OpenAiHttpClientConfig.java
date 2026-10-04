package com.mio.ai.framework.config;

import com.openai.core.Timeout;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 模型 HTTP 客户端超时定制（根源修复"长生成被掐断"）。
 * <p>openai-java(okhttp) 默认 request(call) 超时 60s，覆盖"整次调用"——流式生成超 60s
 * 即被 cancel（StreamReset: CANCEL），前端表现为"连接中断，回答不完整"。
 * 属性链（spring.ai.openai.timeout / chat.timeout）实测未能稳定覆盖默认值，
 * 故经官方 customizer 扩展点直接钉死 okhttp 超时，与 SSE 会话超时对齐。
 */
@Configuration
public class OpenAiHttpClientConfig {

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
            org.slf4j.LoggerFactory.getLogger("MIO_MODEL_TIMEOUT")
                    .info("模型 okhttp 超时已定制: request={}s read={}s", callTimeoutSeconds, Math.min(callTimeoutSeconds, 300));
        };
    }
}
