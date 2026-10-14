package com.mio.ai.bot.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时 ChatModel 工厂：按聊天请求自带的端点配置构建并缓存 ChatModel——
 * OpenCode 免费档走本地反代（反代 server key），自定义供应商的 BaseURL/API Key/API 格式
 * 由前端每次请求自带（仅存浏览器本地，后端瞬态使用不落库）。构建走与自动装配一致的
 * SpringAiOpenAiHttpClient + customizer 扩展点（超时钉死与网关头注入同样生效）。
 */
@Slf4j
@Component
public class RuntimeChatModelFactory {

    private static final String SOURCE_CUSTOM = "custom";

    private static final String FORMAT_OPENAI_COMPAT = "openai";

    /** 自定义供应商的端点配置（瞬态，随请求携带；仅存浏览器本地） */
    public record ProviderEndpoint(String baseUrl, String apiKey, String apiFormat) {
    }

    /** 缓存上限：超过即整体清空重建（供应商配置编辑后旧实例靠此淘汰） */
    private static final int CACHE_MAX = 48;

    private final ChatModel defaultChatModel;
    private final String proxyBaseUrl;
    private final String proxyApiKey;
    private final String engineModel;
    private final long callTimeoutSeconds;
    private final List<OpenAiHttpClientBuilderCustomizer> customizers;
    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    /** key = 端点|api-key|api格式|模型 */
    private final ConcurrentHashMap<String, ChatModel> cache = new ConcurrentHashMap<>();

    public RuntimeChatModelFactory(ChatModel defaultChatModel,
                                   @Value("${spring.ai.openai.base-url}") String proxyBaseUrl,
                                   @Value("${spring.ai.openai.api-key}") String proxyApiKey,
                                   @Value("${spring.ai.openai.chat.model:}") String engineModel,
                                   @Value("${mio.ai.model.call-timeout-seconds:600}") long callTimeoutSeconds,
                                   List<OpenAiHttpClientBuilderCustomizer> customizers,
                                   org.springframework.beans.factory.ObjectProvider<ObservationRegistry> observationRegistry,
                                   org.springframework.beans.factory.ObjectProvider<MeterRegistry> meterRegistry) {
        this.defaultChatModel = defaultChatModel;
        this.proxyBaseUrl = proxyBaseUrl;
        this.proxyApiKey = proxyApiKey;
        this.engineModel = engineModel;
        this.callTimeoutSeconds = callTimeoutSeconds;
        this.customizers = customizers;
        // 未引入 actuator 时无注册表 bean：noop/本地实现兜底，构建照常
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.meterRegistry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
    }

    /** 是否沿用引擎默认 Bean：仅当请求完全没带模型选择参数（旧客户端/标题生成等内部调用）。
     *  显式 source=opencode 的请求即便模型名等于引擎默认也走运行时客户端——
     *  免费档统一走本地反代。 */
    public boolean isEngineDefault(String source, String model) {
        return (source == null || source.isBlank())
                && (model == null || model.isBlank() || model.equals(engineModel));
    }

    /**
     * 解析聊天轮次的 ChatModel 与实际模型名。
     * opencode 免费档走本地反代 + 反代 server key；自定义供应商用请求自带的端点配置
     * （仅存浏览器本地，后端瞬态使用不落库）按 API 格式路由到对应协议适配器。
     *
     * @return model=可用的 ChatModel；resolvedModel=实际使用的模型名（环境信息提示用）
     */
    public Resolved resolve(String source, ProviderEndpoint endpoint, String model) {
        log.info("模型路由: source={} model={} endpoint={} format={}", source, model,
                endpoint == null ? "(反代默认)" : endpoint.baseUrl(),
                endpoint == null ? "openai" : endpoint.apiFormat());
        if (isEngineDefault(source, model)) {
            return new Resolved(defaultChatModel, engineModel);
        }
        String endpointBaseUrl;
        String endpointApiKey;
        String apiFormat;
        if (SOURCE_CUSTOM.equals(source)) {
            if (endpoint == null || endpoint.baseUrl() == null || endpoint.baseUrl().isBlank()) {
                return new Resolved(defaultChatModel, engineModel);
            }
            endpointBaseUrl = endpoint.baseUrl();
            endpointApiKey = endpoint.apiKey() == null ? "" : endpoint.apiKey();
            apiFormat = endpoint.apiFormat() == null || endpoint.apiFormat().isBlank()
                    ? FORMAT_OPENAI_COMPAT : endpoint.apiFormat();
        } else {
            endpointBaseUrl = proxyBaseUrl;
            endpointApiKey = proxyApiKey;
            apiFormat = FORMAT_OPENAI_COMPAT;
        }
        String modelName = model == null ? engineModel : model;
        String key = endpointBaseUrl + "|" + endpointApiKey + "|" + apiFormat + "|" + modelName;
        ChatModel cached = cache.get(key);
        if (cached != null) {
            return new Resolved(cached, modelName);
        }
        ChatModel built;
        try {
            built = build(endpointBaseUrl, endpointApiKey, apiFormat, modelName);
        } catch (com.mio.ai.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 构建失败多为端点配置问题（URL 非法/客户端初始化异常）：记全栈并按业务异常上抛，
            // 调用方回退引擎默认模型继续对话，聊天不再被供应商配置问题打成 500
            log.error("运行时 ChatModel 构建失败: endpoint={} format={} model={}",
                    endpointBaseUrl, apiFormat, modelName, e);
            throw new com.mio.ai.common.exception.BusinessException(
                    com.mio.ai.common.exception.ErrorCode.SYSTEM_ERROR, "模型端点不可用，已回退默认模型");
        }
        if (cache.size() >= CACHE_MAX) {
            cache.clear();
        }
        cache.put(key, built);
        return new Resolved(built, modelName);
    }

    private ChatModel build(String endpointBaseUrl, String endpointApiKey, String apiFormat, String modelName) {
        return switch (apiFormat) {
            case "anthropic" -> new com.mio.ai.framework.zagent.vendor.AnthropicChatModel(
                    endpointBaseUrl, endpointApiKey, modelName);
            case "responses" -> new com.mio.ai.framework.zagent.vendor.ResponsesChatModel(
                    endpointBaseUrl, endpointApiKey, modelName);
            default -> buildOpenAiCompatible(endpointBaseUrl, endpointApiKey, modelName);
        };
    }

    /** OpenAI 兼容端点：与自动装配一致的 customizer 扩展点（超时钉死与网关头注入同样生效）。
     *  opencode 网关直连时自动补 x-api-key 头（其工作区 key 走该头认证，Bearer 会被网关拒绝）。 */
    private ChatModel buildOpenAiCompatible(String endpointBaseUrl, String endpointApiKey, String modelName) {
        SpringAiOpenAiHttpClient.Builder httpBuilder = SpringAiOpenAiHttpClient.builder()
                .observationRegistry(observationRegistry)
                .meterRegistry(meterRegistry);
        for (OpenAiHttpClientBuilderCustomizer customizer : customizers) {
            customizer.customize(httpBuilder);
        }
        if (endpointBaseUrl != null && endpointBaseUrl.contains("opencode.ai") && endpointApiKey != null
                && !endpointApiKey.isBlank()) {
            httpBuilder.interceptor(chain -> chain.proceed(
                    chain.request().newBuilder()
                            .header("x-api-key", endpointApiKey)
                            .build()));
        }
        com.openai.core.ClientOptions clientOptions = com.openai.core.ClientOptions.builder()
                .baseUrl(endpointBaseUrl)
                .apiKey(endpointApiKey)
                .httpClient(httpBuilder.build())
                .build();
        // 同步与异步客户端必须都给：流式走 async 通道，缺省时 builder 会自建无凭证的
        // async client，构建期直接抛 "At least one credential source must be specified"
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(new com.openai.client.OpenAIClientImpl(clientOptions))
                .openAiClientAsync(new com.openai.client.OpenAIClientAsyncImpl(clientOptions))
                .options(OpenAiChatOptions.builder()
                        .model(modelName)
                        .timeout(Duration.ofSeconds(callTimeoutSeconds))
                        .build())
                .build();
        log.info("运行时 ChatModel 已构建: endpoint={} model={}", endpointBaseUrl, modelName);
        return chatModel;
    }

    public record Resolved(ChatModel model, String resolvedModel) {
    }
}
