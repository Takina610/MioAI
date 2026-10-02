package com.mio.ai.superagent.agent.config;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.superagent.model.enums.AgentState;
import com.mio.ai.superagent.util.SseStreams;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: Takina
 * @date: 2026/3/31 16:09
 * @description: 抽象基础代理类，用于管理代理状态和执行流程。
 * <p>提供状态转换、内存管理和基于步骤的执行循环的基础功能。</p>
 * <p>子类必须实现step方法。</p>
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    private String name;

    // 提示词
    private String systemPrompt;
    private String nextStepPrompt;

    // 代理状态
    private AgentState state = AgentState.IDLE;

    // 执行步骤控制
    private int currentStep = 0;
    private int maxSteps = 10;

    // LLM 大模型（2.0 手动工具循环必须直连 ChatModel：ChatClient 会自动挂载 ToolCallAdvisor 执行工具）
    private ChatModel chatModel;

    // 会话记忆（跨请求持久化对话历史；为空时仅使用进程内 messageList）
    private ChatMemory chatMemory;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    // 会话ID
    private String chatId;

    // 当前流式运行的 SSE 连接（runStream 期间设置，供子类发送工具调用等事件）
    protected transient SseEmitter currentEmitter;

    // 事件序号（信封里的 seq，前端可据此排序/去重）
    private final AtomicInteger eventSeq = new AtomicInteger();

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt, String chatId) {
        this.chatId = chatId;
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(SseStreams.CHAT_TIMEOUT_MS);
        this.currentEmitter = sseEmitter;
        // 使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            // 1、基础校验
            try {
                if (this.state != AgentState.IDLE) {
                    sendSseEvent(sseEmitter, "error", "无法从状态运行代理：" + this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sendSseEvent(sseEmitter, "error", "不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }
            } catch (Exception e) {
                sseEmitter.completeWithError(e);
            }
            // 2、执行，更改状态
            this.state = AgentState.RUNNING;
            // 记录消息上下文
            UserMessage userMessage = new UserMessage(userPrompt);
            messageList.add(userMessage);
            persistToMemory(userMessage);
            try {
                // 执行循环
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {} / {}", stepNumber, maxSteps);
                    // 单步执行
                    String stepResult = step();
                    // 获取当前步骤的助手回复（与数据库存储一致）
                    String assistantResponse = getCurrentStepAssistantResponse();
                    // 发送 SSE 事件，返回 AI 的实际回复
                    if (StrUtil.isNotBlank(assistantResponse)) {
                        sendSseEvent(sseEmitter, getCurrentStepType(), assistantResponse);
                    } else if (StrUtil.isNotBlank(stepResult) && !stepResult.equals("思考完成 - 无需行动")) {
                        // 如果没有助手回复但有步骤结果，发送步骤结果
                        sendSseEvent(sseEmitter, "action", stepResult);
                    }
                    // 如果不需要行动，说明已得到最终回复，结束任务
                    if ("思考完成 - 无需行动".equals(stepResult)) {
                        state = AgentState.FINISHED;
                    }
                }
                // 检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sendSseEvent(sseEmitter, "complete", "任务已完成");
                }
                // 发送完成事件
                sendSseEvent(sseEmitter, "done", "任务已完成");
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sendSseEvent(sseEmitter, "error", "执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                // 3、清理资源
                this.cleanup();
                this.currentEmitter = null;
            }
        });

        // 设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            this.cleanup();
            log.warn("SSE connection timeout");
        });
        // 设置完成回调
        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSE connection completed");
        });
        return sseEmitter;
    }

    /**
     * 发送 SSE 事件（统一信封：type/content/seq/ts 序列化为一行 JSON）
     */
    protected void sendSseEvent(SseEmitter sseEmitter, String eventType, String content) throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("content", content);
        event.put("seq", eventSeq.incrementAndGet());
        event.put("ts", System.currentTimeMillis());
        SseStreams.sendTyped(sseEmitter, event);
    }

    /**
     * 把单条消息持久化到会话记忆（ChatMemoryAdvisor 在 2.0 手动循环里不再介入，改由代理自主保存）
     */
    protected void persistToMemory(Message message) {
        if (chatMemory == null || chatId == null || message == null) {
            return;
        }
        try {
            chatMemory.add(chatId, message);
        } catch (Exception e) {
            log.warn("会话记忆写入失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前步骤的助手回复（子类可重写）
     * 返回与数据库存储一致的 AI 回复内容
     */
    protected String getCurrentStepAssistantResponse() {
        return null;
    }

    /**
     * 获取当前步骤的消息类型（子类可重写）
     * 用于区分思考过程、工具执行结果和最终回复
     */
    protected String getCurrentStepType() {
        return "message";
    }

    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }

    /**
     * 运行代理
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public String run(String userPrompt) {
        // 1、基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        // 2、执行，更改状态
        this.state = AgentState.RUNNING;
        // 记录消息上下文
        UserMessage userMessage = new UserMessage(userPrompt);
        messageList.add(userMessage);
        persistToMemory(userMessage);
        // 保存结果列表
        List<String> results = new ArrayList<>();
        try {
            // 执行循环
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);
                // 单步执行
                String stepResult = step();
                // 获取当前步骤的助手回复（与数据库存储一致）
                String assistantResponse = getCurrentStepAssistantResponse();
                if (StrUtil.isNotBlank(assistantResponse)) {
                    results.add(assistantResponse);
                } else if (StrUtil.isNotBlank(stepResult)) {
                    results.add(stepResult);
                }
                // 如果不需要行动，说明已得到最终回复，结束任务
                if ("思考完成 - 无需行动".equals(stepResult)) {
                    state = AgentState.FINISHED;
                }
            }
            // 检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("任务已完成");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {
            // 3、清理资源
            this.cleanup();
        }
    }
}
