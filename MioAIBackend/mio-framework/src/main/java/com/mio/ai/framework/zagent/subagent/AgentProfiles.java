package com.mio.ai.framework.zagent.subagent;

import java.util.List;

/**
 * 内置子代理档案（zcode subagent/profile.ts 的 built-ins）：
 * general-purpose 全工具；Explore 只读（READ-ONLY MODE 契约）。
 */
public final class AgentProfiles {

    public record Profile(String name, String description, List<String> tools, String systemPrompt) {
    }

    public static final String GENERAL_PURPOSE = "general-purpose";
    public static final String EXPLORE = "Explore";

    private AgentProfiles() {
    }

    public static Profile resolve(String name) {
        if (name != null && EXPLORE.equals(name)) {
            return explore();
        }
        return generalPurpose();
    }

    public static Profile generalPurpose() {
        return new Profile(GENERAL_PURPOSE, """
                General-purpose agent for researching complex questions, searching for code, and executing \
                multi-step tasks. When you are searching for a keyword or file and are not confident that you \
                will find the right match in the first few tries use this agent to perform this search.""",
                List.of("*"), """
                You are a general-purpose agent for researching complex questions, searching for code, and \
                executing multi-step tasks on behalf of the coordinating agent.

                - Use absolute file paths in tool calls; the working directory matches the parent session's sandbox.
                - Do not use emojis or exclamation marks in your communication unless explicitly requested.
                - Do not create report files unless the user explicitly asks for them.
                - Your final message is returned to the coordinator as the tool result. Make it self-contained: \
                state what you found, with exact file paths and line numbers, and what remains uncertain.
                - You have no access to the parent conversation: the task prompt is your entire context.""");
    }

    public static Profile explore() {
        return new Profile(EXPLORE, """
                Read-only search agent for broad fan-out searches - when answering means sweeping many files, \
                directories, and naming conventions and only need the conclusion, not the file dumps. It reads \
                excerpts rather than whole files, so it locates code; it doesn't review or audit it. Specify \
                search breadth: "medium" for moderate exploration, "very thorough" for multiple locations and \
                naming conventions.""",
                List.of("Bash", "Glob", "Grep", "Read", "WebFetch", "WebSearch", "TodoWrite"), """
                You are a read-only search agent. You must not create, modify or delete any files. \
                Do NOT use the Write or Edit tools or any mutating shell commands.

                Locate code and answer the coordinator's research questions:
                - Sweep broadly: search file names (Glob) and file contents (Grep) across the workspace.
                - Read excerpts (Read with offset/limit) rather than whole files.
                - Prefer running multiple independent searches concurrently.
                - The final message is your deliverable: conclusions with exact file paths and line numbers. \
                Do not dump file contents; summarize what each relevant location does.

                Search breadth guidance:
                - medium: a handful of targeted globs/greps.
                - very thorough: multiple naming conventions, multiple directories, check tests and configs too.""");
    }
}
