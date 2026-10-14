package com.mio.ai.framework.zagent.vendor;

import com.mio.ai.common.utils.JacksonUtil;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 供应商 SSE 协议适配器基类：把非 OpenAI 兼容端点（Anthropic Messages / OpenAI Responses）
 * 映射为引擎可消费的 Spring ChatResponse 流。契约与引擎对齐——
 * 文本增量走 AssistantMessage.text、思考增量走 metadata "reasoningContent"、
 * 工具调用按 OpenAI 流式形状（首个分片带 id/name，参数分片空 id 续片），
 * 工具执行循环由 AgentEngine 负责，适配器不做内部工具执行。
 * <p>适配器实例被工厂缓存复用，流内可变状态一律放 {@link #newSession()} 的会话对象。
 */
public abstract class VendorSseChatModel implements ChatModel {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(VendorSseChatModel.class);

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";

    /** 适配器共享的 HTTP 客户端：长 read 超时对齐流式生成；
     *  interceptor 注入网关头（浏览器 UA + x-opencode-session 会话路由），
     *  与引擎 openai 兼容客户端的 OpenAiHttpClientConfig 行为一致，对其他端点无害 */
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(chain -> chain.proceed(
                    chain.request().newBuilder()
                            .header("User-Agent", BROWSER_UA)
                            .header("x-opencode-session", "mioai-agent")
                            .build()))
            .build();

    /** SSE 读取线程：Flux.create 的 sink 允许跨线程串行发射 */
    private static final ExecutorService READER = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "vendor-sse-reader");
        t.setDaemon(true);
        return t;
    });

    protected final String baseUrl;
    protected final String apiKey;
    protected final String defaultModel;

    protected VendorSseChatModel(String baseUrl, String apiKey, String defaultModel) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.create(emitter -> {
            Request request;
            try {
                request = buildRequest(prompt);
            } catch (Exception e) {
                emitter.error(e);
                return;
            }
            logRequest(request);
            Call call = HTTP.newCall(request);
            emitter.onDispose(call::cancel);
            Object session = newSession();
            READER.submit(() -> readStream(call, session, emitter));
        });
    }

    /** 引擎按 prompt.options 变异默认项：不提供时引擎落入裸 OpenAiChatOptions
     *  （默认模型 gpt-5-mini）覆盖配置的模型名——必须暴露带模型的默认项。 */
    @Override
    public OpenAiChatOptions getDefaultOptions() {
        return OpenAiChatOptions.builder().model(defaultModel).build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 引擎只走流式；call 供非流式调用方兜底：聚合流为单条响应
        StringBuilder text = new StringBuilder();
        stream(prompt).doOnNext(chunk -> {
            if (chunk.getResult() != null && chunk.getResult().getOutput() != null
                    && chunk.getResult().getOutput().getText() != null) {
                text.append(chunk.getResult().getOutput().getText());
            }
        }).blockLast();
        AssistantMessage message = AssistantMessage.builder().content(text.toString()).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private void readStream(Call call, Object session, FluxSink<ChatResponse> emitter) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                String body = response.body() == null ? "" : response.body().string();
                emitter.error(new IllegalStateException("上游错误 HTTP " + response.code() + ": "
                        + abbreviate(body, 300)));
                return;
            }
            var source = response.body().source();
            while (!emitter.isCancelled()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    try {
                        handleEventData(session, JacksonUtil.readValue(data, Map.class), emitter);
                    } catch (Exception e) {
                        // 单条事件解析失败不中断整流（个别网关会夹带非常规事件）
                    }
                }
            }
            onFinish(session, emitter::next);
            emitter.complete();
        } catch (Exception e) {
            if (!emitter.isCancelled()) {
                emitter.error(e);
            }
        }
    }

    /** 诊断：打出上游请求的关键字段（模型/工具数/体前 1800 字符） */
    private void logRequest(Request request) {
        try {
            String body = request.body() == null ? "" : request.body().toString();
            if (request.body() != null) {
                okio.Buffer buf = new okio.Buffer();
                request.body().writeTo(buf);
                body = buf.readUtf8();
            }
            log.info("供应商请求: url={} body[{}]", request.url(), abbreviate(body, 1800));
        } catch (Exception e) {
            log.info("供应商请求: url={} (body 读取失败: {})", request.url(), e.getMessage());
        }
    }

    /** 每次流式调用的可变状态（用量累积、工具调用聚合等） */
    protected abstract Object newSession();

    /** 构造该供应商的流式请求（含鉴权头与请求体） */
    protected abstract Request buildRequest(Prompt prompt) throws Exception;

    /** 处理一条 SSE data 事件（已解析为 JSON Map） */
    protected abstract void handleEventData(Object session, Map<String, Object> event, FluxSink<ChatResponse> sink);

    /** 流结束（连接关闭）前的收尾发射：兜底补 finishReason 与用量（正常流通常已在事件里发过） */
    protected abstract void onFinish(Object session, Consumer<ChatResponse> sink);

    // ---------- 分片构造助手（三类增量 + 结束片） ----------

    protected ChatResponse textChunk(String delta) {
        AssistantMessage message = AssistantMessage.builder().content(delta).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    protected ChatResponse thinkingChunk(String delta) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .properties(Map.of("reasoningContent", delta))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    protected ChatResponse toolChunk(AssistantMessage.ToolCall call) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(call))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    protected ChatResponse finishChunk(String finishReason, Integer promptTokens, Integer completionTokens) {
        AssistantMessage message = AssistantMessage.builder().content("").build();
        Generation generation = new Generation(message,
                ChatGenerationMetadata.builder().finishReason(finishReason).build());
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder();
        if (promptTokens != null || completionTokens != null) {
            metadata.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(List.of(generation), metadata.build());
    }

    // ---------- 请求侧公共助手 ----------

    /** 引擎传入的 OpenAI 形状选项：模型名 / 推理档 / 工具列表 */
    protected record RequestSpec(String model, String reasoningEffort, List<ToolCallback> tools) {
    }

    protected RequestSpec specOf(Prompt prompt) {
        OpenAiChatOptions options = prompt.getOptions() instanceof OpenAiChatOptions opts ? opts : null;
        String model = options != null && options.getModel() != null && !options.getModel().isBlank()
                ? options.getModel() : defaultModel;
        String effort = options == null || options.getReasoningEffort() == null
                ? null : options.getReasoningEffort();
        List<ToolCallback> tools = options == null || options.getToolCallbacks() == null
                ? List.of() : options.getToolCallbacks();
        return new RequestSpec(model, effort, tools);
    }

    /** 工具 schema 字符串 → JSON 对象（Responses/Anthropic 的 parameters/input_schema 需要对象） */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> schemaOf(ToolCallback callback) {
        try {
            Map<String, Object> schema = JacksonUtil.readValue(callback.getToolDefinition().inputSchema(), Map.class);
            return schema != null ? schema : Map.of();
        } catch (Exception e) {
            return Map.of("type", "object");
        }
    }

    protected static int intOf(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    protected static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String abbreviate(String s, int max) {
        String flat = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max);
    }
}
