package com.mio.ai.superagent.agent.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.customagent.model.entity.AgentUsageLog;
import com.mio.ai.customagent.model.entity.ToolCallLog;
import com.mio.ai.customagent.service.log.AgentUsageLogService;
import com.mio.ai.customagent.service.log.ToolCallLogService;
import com.mio.ai.superagent.model.enums.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author: Takina
 * @date: 2026/3/31 16:17
 * @description: 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具（非 final：MioManus 等子类可追加实例专属工具，如规划工具）
    private ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    // 使用日志服务
    private AgentUsageLogService agentUsageLogService;
    private ToolCallLogService toolCallLogService;

    // 调用上下文
    private Long agentId;
    private Long userId;
    private Long conversationId;

    public ToolCallAgent(ToolCallback[] availableTools) {
        this(availableTools, null, null, null, null, null);
    }

    public ToolCallAgent(ToolCallback[] availableTools,
                         AgentUsageLogService agentUsageLogService,
                         ToolCallLogService toolCallLogService,
                         Long agentId,
                         Long userId,
                         Long conversationId) {
        super();
        this.availableTools = availableTools;
        this.agentUsageLogService = agentUsageLogService;
        this.toolCallLogService = toolCallLogService;
        this.agentId = agentId;
        this.userId = userId;
        this.conversationId = conversationId;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
//                .withInternalToolExecutionEnabled(false)
                .internalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 当前步骤的助手消息（用于返回给前端）
     */
    private AssistantMessage currentStepAssistantMessage;

    /**
     * 当前步骤是否需要执行行动（调用工具）
     */
    private boolean currentStepNeedAct = false;

    /**
     * 敏感词拦截标识（与 ChineseSafeGuardAdvisor 中的响应消息匹配）
     */
    private static final String SENSITIVE_CONTENT_FLAG = "敏感内容";

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、调用 AI 大模型，获取工具调用结果
        // NEXT_STEP_PROMPT 作为系统内部提示，不添加到消息列表，避免被记录到数据库
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        long startTime = System.currentTimeMillis();
        AgentUsageLog usageLog = new AgentUsageLog();
        usageLog.setAgentId(agentId);
        usageLog.setUserId(userId);
        usageLog.setConversationId(conversationId);
        usageLog.setStatus(1);
        usageLog.setCreateTime(new Date());

        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt() + "\n\n" + (StrUtil.isNotBlank(getNextStepPrompt()) ? getNextStepPrompt() : ""))
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, this.getChatId()))
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();

            // 记录 token 使用情况
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                if (usage.getPromptTokens() != null) {
                    usageLog.setInputTokens(usage.getPromptTokens().intValue());
                }
                if (usage.getCompletionTokens() != null) {
                    usageLog.setOutputTokens(usage.getCompletionTokens().intValue());
                }
            }

            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 保存当前步骤的助手消息（用于返回给前端）
            this.currentStepAssistantMessage = assistantMessage;
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            // 检测是否被敏感词拦截
            if (result != null && result.contains(SENSITIVE_CONTENT_FLAG)) {
                log.warn("检测到敏感词拦截，终止执行");
                setState(AgentState.FINISHED);
                getMessageList().add(assistantMessage);
                return false;
            }
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);

            // 记录工具调用日志
            logToolCalls(toolCallList);

            // 如果不需要调用工具，返回 false
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                this.currentStepNeedAct = false;
                saveUsageLog(usageLog, startTime, null);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                this.currentStepNeedAct = true;
                saveUsageLog(usageLog, startTime, null);
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            saveUsageLog(usageLog, startTime, e.getMessage());
            return false;
        }
    }

    private void saveUsageLog(AgentUsageLog usageLog, long startTime, String errorMsg) {
        usageLog.setResponseTime((int) (System.currentTimeMillis() - startTime));
        if (errorMsg != null) {
            usageLog.setStatus(0);
            usageLog.setErrorMsg(errorMsg);
        }
        if (agentUsageLogService != null) {
            agentUsageLogService.logUsage(usageLog);
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态，同时标记当前步骤为最终回复
            setState(AgentState.FINISHED);
            this.currentStepNeedAct = false;
        }
        // 优化日志输出，返回更友好的消息
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> {
                    String toolName = response.name();
                    String responseData = response.responseData();
                    log.info("工具 {} 执行完成", toolName);
                    return formatToolResult(toolName, responseData);
                })
                .collect(Collectors.joining("\n"));
        return results;
    }

    private void logToolCalls(List<AssistantMessage.ToolCall> toolCallList) {
        if (toolCallLogService == null || toolCallList == null || toolCallList.isEmpty()) {
            return;
        }

        for (AssistantMessage.ToolCall toolCall : toolCallList) {
            ToolCallLog log = new ToolCallLog();
            log.setAgentId(agentId);
            log.setUserId(userId);
            log.setConversationId(conversationId);
            log.setToolId(0L);
            log.setName(toolCall.name());
            log.setInputParams(toolCall.arguments());
            log.setStatus(1);
            log.setCreateTime(new Date());
            toolCallLogService.logToolCall(log);
        }
    }

    /**
     * 格式化工具调用结果，返回更友好的消息
     */
    private String formatToolResult(String toolName, String responseData) {
        if (responseData == null || responseData.isEmpty()) {
            return "已完成 " + toolName + " 操作。";
        }
        if (responseData.length() > 500) {
            return "已完成 " + toolName + " 操作，获取到相关数据。";
        }
        return "已完成 " + toolName + " 操作：" + responseData;
    }

    /**
     * 获取当前步骤的助手消息文本（用于返回给前端）
     */
    public String getCurrentStepResponse() {
        if (currentStepAssistantMessage != null) {
            String text = currentStepAssistantMessage.getText();
            if (StrUtil.isNotBlank(text)) {
                // 如果任务已结束（调用了 terminate 工具），过滤掉末尾的 "terminate" 字样
                if (getState() == AgentState.FINISHED) {
                    text = text.replaceAll("(?i)\\s*terminate\\s*$", "").trim();
                }
                return text;
            }
        }
        return null;
    }

    /**
     * 重写父类方法，返回与数据库存储一致的 AI 回复内容
     */
    @Override
    protected String getCurrentStepAssistantResponse() {
        return getCurrentStepResponse();
    }

    /**
     * 重写父类方法，返回当前步骤的消息类型
     * 不需要调用工具时为最终回复(final)，需要调用工具时为思考过程(thinking)
     */
    @Override
    protected String getCurrentStepType() {
        return this.currentStepNeedAct ? "thinking" : "final";
    }
}

