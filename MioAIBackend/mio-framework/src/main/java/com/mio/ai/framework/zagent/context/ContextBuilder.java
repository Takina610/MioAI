package com.mio.ai.framework.zagent.context;

import com.mio.ai.framework.zagent.tools.SandboxFs;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文构建器（zcode ContextBuilder 的对位移植）：
 * 系统消息三段（cli 前缀 / 稳定身份 / 动态段）+ 附件（AGENTS.md + currentDate、知识库）。
 * 自定义智能体的 system_prompt 走 zcode customSystemPrompt 路径：
 * 替换身份与动态段，环境事实仍以附件注入。
 */
public final class ContextBuilder {

    public record Built(List<Message> systemMessages, List<String> attachmentBodies) {
    }

    private final String customSystemPrompt;
    private final String knowledgeContext;
    private final SandboxFs fs;
    private final String modelName;

    public ContextBuilder(String customSystemPrompt, String knowledgeContext, SandboxFs fs, String modelName) {
        this.customSystemPrompt = customSystemPrompt;
        this.knowledgeContext = knowledgeContext;
        this.fs = fs;
        this.modelName = modelName;
    }

    public Built build() {
        List<Message> systemMessages = new ArrayList<>();
        systemMessages.add(new SystemMessage(SystemPrompts.CLI_PREFIX));
        if (customSystemPrompt != null && !customSystemPrompt.isBlank()) {
            systemMessages.add(new SystemMessage(customSystemPrompt.strip()));
        } else {
            systemMessages.add(new SystemMessage(SystemPrompts.IDENTITY));
            systemMessages.add(new SystemMessage(dynamicBody()));
        }

        List<String> attachments = new ArrayList<>();
        String userContext = buildUserContext();
        if (userContext != null && !userContext.isBlank()) {
            attachments.add(userContext);
        }
        if (knowledgeContext != null && !knowledgeContext.isBlank()) {
            attachments.add(knowledgeContext);
        }
        return new Built(systemMessages, attachments);
    }

    /** 动态系统段：沟通纪律 + 环境 + 上下文管理 + git 上下文 */
    private String dynamicBody() {
        StringBuilder sb = new StringBuilder(SystemPrompts.COMMUNICATING);
        sb.append("\n\n").append(SystemPrompts.envInfoSection(
                fs != null ? fs.workdirDisplay() : null,
                fs != null && SandboxEnvProbe.isGitRepo(fs),
                "linux",
                fs != null ? SandboxEnvProbe.osVersion(fs) : null,
                modelName,
                fs != null));
        sb.append("\n\n").append(SystemPrompts.CONTEXT_MANAGEMENT);
        String gitContext = fs != null ? SandboxEnvProbe.gitContextLines(fs) : null;
        if (gitContext != null) {
            sb.append("\n\n").append(gitContext);
        }
        return sb.toString();
    }

    /** zcode request-user-context 附件：AGENTS.md + currentDate */
    private String buildUserContext() {
        String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String agentsMd = fs != null ? SandboxEnvProbe.agentsMd(fs) : null;
        if (agentsMd == null) {
            return SystemPrompts.currentDateSection(date);
        }
        return "# agentsMd\n" + agentsMd + "\n\n" + SystemPrompts.currentDateSection(date);
    }

    /** 附件 → user 角色消息（zcode：以 &lt;system-reminder&gt; 包裹渲染） */
    public static List<Message> attachmentMessages(List<String> bodies) {
        List<Message> messages = new ArrayList<>();
        for (String body : bodies) {
            messages.add(new UserMessage("<system-reminder>\n" + body + "\n</system-reminder>"));
        }
        return messages;
    }
}
