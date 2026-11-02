package com.mio.ai.bot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 关键配置自检：启动完成时提示缺失项，避免"功能到用户手里才炸"。
 * <p>knowledge 检索/向量化强依赖 embedding 配置；缺失时启动日志给明确告警，
 * 而不是等到用户上传文档才发现（原实现里空 env 会静默回落到内置默认 key，
 * 问题只在运行期以 429/无渠道的形式暴露，极难定位）。
 */
@Slf4j
@Component
public class StartupConfigCheck {

    @Value("${spring.ai.openai.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${spring.ai.openai.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${spring.ai.openai.embedding.model:}")
    private String embeddingModel;

    @EventListener(ApplicationReadyEvent.class)
    public void checkEmbeddingConfig() {
        if (embeddingApiKey == null || embeddingApiKey.isBlank()) {
            log.warn("""
                    [配置告警] 未配置 embedding API Key（spring.ai.openai.embedding.api-key）。
                    影响：知识库检索与文档向量化将不可用。
                    解决：在 .env 或容器环境变量中设置 EMBED_APIKEY=<有效密钥>。""");
            return;
        }
        log.info("embedding 配置就绪: base-url={}, model={}", embeddingBaseUrl, embeddingModel);
    }
}
