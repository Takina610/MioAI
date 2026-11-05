package com.mio.ai.framework.zagent.tools.contract;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.sandbox.SandboxSession;
import com.mio.ai.framework.zagent.AgentEvents;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.hooks.AgentHooks;
import com.mio.ai.framework.zagent.subagent.SubagentLauncher;
import com.mio.ai.framework.zagent.task.BackgroundTasks;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import com.mio.ai.framework.zagent.tools.delegation.AgentTool;
import com.mio.ai.framework.zagent.tools.delegation.AskUserQuestionTool;
import com.mio.ai.framework.zagent.tools.delegation.TaskOutputTool;
import com.mio.ai.framework.zagent.tools.delegation.TaskStopTool;
import com.mio.ai.framework.zagent.tools.delegation.TodoTools;
import com.mio.ai.framework.zagent.tools.fs.BashTool;
import com.mio.ai.framework.zagent.tools.fs.EditTool;
import com.mio.ai.framework.zagent.tools.fs.GlobTool;
import com.mio.ai.framework.zagent.tools.fs.GrepTool;
import com.mio.ai.framework.zagent.tools.fs.ReadFileState;
import com.mio.ai.framework.zagent.tools.fs.ReadTool;
import com.mio.ai.framework.zagent.tools.fs.SandboxFs;
import com.mio.ai.framework.zagent.tools.fs.WriteTool;
import com.mio.ai.framework.zagent.tools.web.WebFetchTool;
import com.mio.ai.framework.zagent.tools.web.WebSearchTool;

/**
 * 工具集工厂（zcode registerBuiltInTools 的对位移植）：
 * 按运行上下文组装内置工具 + MCP 工具；沙箱未启用时文件/命令/搜索工具不注册；
 * 子代理按档案工具白名单裁剪且不再嵌套 Agent。
 */
@Component
public final class ToolsetFactory {

    public record RunContext(String chatId, SandboxFs fs, List<ToolCallback> mcpTools,
                             BackgroundTasks tasks, AgentHooks hooks, String ownerKey) {

        public RunContext(String chatId, SandboxFs fs, List<ToolCallback> mcpTools, BackgroundTasks tasks) {
            this(chatId, fs, mcpTools, tasks, null, null);
        }

        public RunContext(String chatId, SandboxFs fs, List<ToolCallback> mcpTools, BackgroundTasks tasks,
                          AgentHooks hooks) {
            this(chatId, fs, mcpTools, tasks, hooks, null);
        }
    }

    private final ChatModel chatModel;
    private final ObjectProvider<SandboxSession> sandboxProvider;
    private final String searchApiUrl;
    private final String searchApiKey;

    public ToolsetFactory(ChatModel chatModel,
                          ObjectProvider<SandboxSession> sandboxProvider,
                          @Value("${web.search.url:}") String searchApiUrl,
                          @Value("${web.search.api-key:}") String searchApiKey) {
        this.chatModel = chatModel;
        this.sandboxProvider = sandboxProvider;
        this.searchApiUrl = searchApiUrl;
        this.searchApiKey = searchApiKey;
    }

    /** 沙箱文件系统（未启用返回 null） */
    public SandboxFs sandboxFs() {
        SandboxSession session = sandboxProvider.getIfAvailable();
        return session == null ? null : new SandboxFs(session, workdirName());
    }

    private String workdirName() {
        SandboxSession session = sandboxProvider.getIfAvailable();
        return session != null ? StrUtil.subAfter(session.workdirDisplay(), "/", true) : "sandbox";
    }

    /**
     * 组装一次运行的注册表。
     *
     * @param allowedTools 工具白名单（null/"*" = 全量；子代理档案传入裁剪清单）
     * @param launcher     子代理启动器（null = 本运行不注册 Agent 工具）
     * @param events       引擎事件通道（null = 非交互运行，不注册 AskUserQuestion）
     * @param hooks        工具调用钩子（null = 无钩子；子代理注册表沿用运行级钩子）
     */
    public ToolRegistry build(String chatId, ConversationState state, List<ToolCallback> mcpTools,
                              List<String> allowedTools, SubagentLauncher launcher, AgentEvents events,
                              AgentHooks hooks) {
        return build(chatId, state, mcpTools, allowedTools, launcher, events, hooks, null);
    }

    /**
     * 组装一次运行的注册表。
     *
     * @param allowedTools 工具白名单（null/"*" = 全量；子代理档案传入裁剪清单）
     * @param launcher     子代理启动器（null = 本运行不注册 Agent 工具）
     * @param events       引擎事件通道（null = 非交互运行，不注册 AskUserQuestion）
     * @param hooks        工具调用钩子（null = 无钩子；子代理注册表沿用运行级钩子）
     * @param ownerKey     轮次归属标识（交互工具据此绑定发起人；null = 不校验）
     */
    public ToolRegistry build(String chatId, ConversationState state, List<ToolCallback> mcpTools,
                              List<String> allowedTools, SubagentLauncher launcher, AgentEvents events,
                              AgentHooks hooks, String ownerKey) {
        return build(chatId, state, mcpTools, allowedTools, launcher, events, hooks, ownerKey, 0, 0);
    }

    public ToolRegistry build(String chatId, ConversationState state, List<ToolCallback> mcpTools,
                              List<String> allowedTools, SubagentLauncher launcher, AgentEvents events,
                              AgentHooks hooks, String ownerKey, long delegationTimeoutMs) {
        return build(chatId, state, mcpTools, allowedTools, launcher, events, hooks, ownerKey,
                delegationTimeoutMs, 0);
    }

    /**
     * 组装一次运行的注册表。
     *
     * @param allowedTools        工具白名单（null/"*" = 全量；子代理档案传入裁剪清单）
     * @param launcher            子代理启动器（null = 本运行不注册 Agent 工具）
     * @param events              引擎事件通道（null = 非交互运行，不注册 AskUserQuestion）
     * @param hooks               工具调用钩子（null = 无钩子；子代理注册表沿用运行级钩子）
     * @param ownerKey            轮次归属标识（交互工具据此绑定发起人；null = 不校验）
     * @param delegationTimeoutMs 单次委派墙钟预算（&lt;=0 不限时）；子代理运行不再嵌套 Agent，
     *                            因此子级传 0
     * @param maxConcurrent      单轮并行委派上限（&lt;=0 不限）；子代理运行传 0
     */
    public ToolRegistry build(String chatId, ConversationState state, List<ToolCallback> mcpTools,
                              List<String> allowedTools, SubagentLauncher launcher, AgentEvents events,
                              AgentHooks hooks, String ownerKey, long delegationTimeoutMs,
                              int maxConcurrent) {
        return build(chatId, state, mcpTools, allowedTools, launcher, events, hooks, ownerKey,
                delegationTimeoutMs, maxConcurrent, null);
    }

    /**
     * 组装一次运行的注册表。
     *
     * @param allowedTools        工具白名单（null/"*" = 全量；子代理档案传入裁剪清单）
     * @param launcher            子代理启动器（null = 本运行不注册 Agent 工具）
     * @param events              引擎事件通道（null = 非交互运行，不注册 AskUserQuestion）
     * @param hooks               工具调用钩子（null = 无钩子；子代理注册表沿用运行级钩子）
     * @param ownerKey            轮次归属标识（交互工具据此绑定发起人；null = 不校验）
     * @param delegationTimeoutMs 单次委派墙钟预算（&lt;=0 不限时）；子代理运行不再嵌套 Agent，
     *                            因此子级传 0
     * @param maxConcurrent      单轮并行委派上限（&lt;=0 不限）；子代理运行传 0
     * @param agentDescription    Agent 工具描述覆盖（null = 内置档案描述）；群聊传成员名单描述
     */
    public ToolRegistry build(String chatId, ConversationState state, List<ToolCallback> mcpTools,
                              List<String> allowedTools, SubagentLauncher launcher, AgentEvents events,
                              AgentHooks hooks, String ownerKey, long delegationTimeoutMs,
                              int maxConcurrent, String agentDescription) {
        SandboxFs fs = sandboxFs();
        ToolContext context = new ToolContext(chatId, state, ReadFileState.forChat(chatId), fs,
                BackgroundTasks.instance(), launcher, events, ownerKey);
        ToolRegistry registry = new ToolRegistry(context, hooks);
        boolean all = allowedTools == null || allowedTools.isEmpty() || allowedTools.contains("*");

        if (fs != null) {
            // Read 也受白名单约束：群聊调度层（Router）用白名单把环境工具整体摘掉，
            // 只保留调度与对外交互能力，具体执行全部交给成员（Worker）
            if (all || allowedTools.contains("Read")) {
                registry.register(ReadTool.entry());
            }
            if (all || allowedTools.contains("Write")) {
                registry.register(WriteTool.entry());
            }
            if (all || allowedTools.contains("Edit")) {
                registry.register(EditTool.entry());
            }
            if (all || allowedTools.contains("Bash")) {
                registry.register(new BashTool().entry());
            }
            if (all || allowedTools.contains("Glob")) {
                registry.register(GlobTool.entry());
            }
            if (all || allowedTools.contains("Grep")) {
                registry.register(GrepTool.entry());
            }
        }
        if (all || allowedTools.contains("WebFetch")) {
            registry.register(new WebFetchTool(chatModel).entry());
        }
        if (all || allowedTools.contains("WebSearch")) {
            registry.register(new WebSearchTool(searchApiUrl, searchApiKey).entry());
        }
        if (all || allowedTools.contains("TodoRead")) {
            registry.register(TodoTools.readEntry());
        }
        if (all || allowedTools.contains("TodoWrite")) {
            registry.register(TodoTools.writeEntry());
        }
        if (all || allowedTools.contains("TaskOutput")) {
            registry.register(TaskOutputTool.entry());
        }
        if (all || allowedTools.contains("TaskStop")) {
            registry.register(TaskStopTool.entry());
        }
        if (launcher != null && (all || allowedTools.contains("Agent"))) {
            registry.register(new AgentTool(launcher, delegationTimeoutMs, maxConcurrent,
                    agentDescription).entry());
        }
        if (events != null && (all || allowedTools.contains("AskUserQuestion"))) {
            registry.register(AskUserQuestionTool.entry());
        }
        registry.registerMcpCallbacks(mcpTools);
        return registry;
    }
}
