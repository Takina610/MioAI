package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.subagent.SubagentLauncher;
import com.mio.ai.framework.zagent.task.BackgroundTasks;

/**
 * 工具执行上下文：一次引擎运行共享的会话级状态与设施。
 * <p>无状态依赖（模型客户端、搜索后端等）由工厂在构造处理器时闭包捕获，
 * 这里只放随会话变化的量。
 */
public final class ToolContext {

    public final String chatId;
    public final ConversationState state;
    public final ReadFileState readFileState;
    /** 沙箱文件系统；沙箱未启用时为 null（文件类工具不会注册） */
    public final SandboxFs fs;
    public final BackgroundTasks tasks;
    public final SubagentLauncher subagents;

    public ToolContext(String chatId, ConversationState state, ReadFileState readFileState,
                       SandboxFs fs, BackgroundTasks tasks, SubagentLauncher subagents) {
        this.chatId = chatId;
        this.state = state;
        this.readFileState = readFileState;
        this.fs = fs;
        this.tasks = tasks;
        this.subagents = subagents;
    }
}
