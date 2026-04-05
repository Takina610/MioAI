package com.mio.ai.superagent.agent.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.superagent.model.enums.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    // LLM 大模型
    private ChatClient chatClient;

    // Memory 记忆（需要自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    // 会话ID
    private String chatId;

    // JSON 序列化工具
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 运行代理（流式输出）
     *
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public SseEmitter runStream(String userPrompt, String chatId) {
        this.chatId = chatId;
        // 创建一个超时时间较长的 SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L); // 5 分钟超时
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
            messageList.add(new UserMessage(userPrompt));
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
                        sendSseEvent(sseEmitter, "message", assistantResponse);
                    } else if (StrUtil.isNotBlank(stepResult) && !stepResult.equals("思考完成 - 无需行动")) {
                        // 如果没有助手回复但有步骤结果，发送步骤结果
                        sendSseEvent(sseEmitter, "action", stepResult);
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
     * 发送 SSE 事件
     */
    protected void sendSseEvent(SseEmitter sseEmitter, String eventType, String content) throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("content", content);
        event.put("timestamp", System.currentTimeMillis());
        sseEmitter.send(SseEmitter.event()
                .name("message")
                .data(content));
    }

    /**
     * 获取当前步骤的助手回复（子类可重写）
     * 返回与数据库存储一致的 AI 回复内容
     */
    protected String getCurrentStepAssistantResponse() {
        return null;
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
        messageList.add(new UserMessage(userPrompt));
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
