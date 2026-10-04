package com.mio.ai.framework.zagent.tools;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.sandbox.SandboxSession;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.subagent.SubagentLauncher;
import com.mio.ai.framework.zagent.task.BackgroundTasks;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具集工厂（zcode registerBuiltInTools 的对位移植）：
 * 按运行上下文组装内置工具 + MCP 工具；沙箱未启用时文件/命令/搜索工具不注册；
 * 子代理按档案工具白名单裁剪且不再嵌套 Agent。
 */
@Component
public final class ToolsetFactory {

    public record RunContext(String chatId, SandboxFs fs, List<ToolCallback> mcpTools,
                             BackgroundTasks tasks) {
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
     */
    public ToolRegistry build(String chatId, ConversationState state, List<ToolCallback> mcpTools,
                              List<String> allowedTools, SubagentLauncher launcher) {
        SandboxFs fs = sandboxFs();
        ToolContext context = new ToolContext(chatId, state, ReadFileState.forChat(chatId), fs,
                BackgroundTasks.instance(), launcher);
        ToolRegistry registry = new ToolRegistry(context);
        boolean all = allowedTools == null || allowedTools.isEmpty() || allowedTools.contains("*");

        if (fs != null) {
            registry.register(ReadTool.entry());
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
            registry.register(new AgentTool(launcher).entry());
        }
        registry.registerMcpCallbacks(mcpTools);
        return registry;
    }
}
