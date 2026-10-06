package com.mio.ai.framework.zagent.tools;

/**
 * 工具描述（zcode 内置工具 description 逐字照搬；仅按实际注册的能力裁剪行）。
 * <p>描述质量直接决定模型选工具的能力——修改前先对照
 * agent-refs/zcode/apps/zcode-cli/packages/core/src/tool/handlers。
 */
final class ToolDescriptions {

    private ToolDescriptions() {
    }

    static final String READ = """
            Reads a file from the local filesystem.

            - `file_path` must be an absolute path.
            - Reads up to 2000 lines by default.
            - You can optionally specify a line offset and limit (especially handy for long files), but it's recommended to read the whole file by not providing these parameters
            - Results are returned using cat -n format, with line numbers starting at 1
            - Reads images (PNG, JPG, …) and presents them visually.
            - Reading a directory, a missing file, or an empty file returns an error or system reminder rather than content.
            - Do NOT re-read a file you just edited to verify — Edit/Write would have errored if the change failed, and the harness tracks file state for you.""";

    static final String WRITE = """
            Writes a file to the local filesystem, overwriting if one exists.

            When to use: creating a new file, or fully replacing one you've already Read. Overwriting an existing file you haven't Read will fail. For partial changes, use Edit instead.""";

    static final String EDIT = """
            Performs exact string replacement in a file.

            - You must Read the file in this conversation before editing, or the call will fail.
            - `old_string` must match the file exactly, including indentation, and be unique — the edit fails otherwise. Strip the Read line prefix (line number + tab) before matching.
            - `replace_all: true` replaces every occurrence instead.""";

    static final String BASH = """
            Executes a bash command and returns its output.

            - Working directory persists between calls, but prefer absolute paths — `cd` in a compound command can trigger a permission prompt. Shell state (env vars, functions) does not persist; the shell is initialized from the user's profile.
            - IMPORTANT: Avoid using this tool to run `find`, `grep`, `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or after you have verified that a dedicated tool cannot accomplish your task. Instead, use the appropriate dedicated tool as this will provide a much better experience for the user.
            - `timeout` is in milliseconds: default 120000, max 600000.
            - `run_in_background` runs the command detached: it keeps running across turns and re-invokes you when it exits. No `&` needed.

            # Git
            - Interactive flags (`-i`, e.g. `git rebase -i`, `git add -i`) are not supported in this environment.
            - Use the `gh` CLI for GitHub operations (PRs, issues, API).
            - Commit or push only when the user asks. If on the default branch, branch first.""";

    static final String BASH_TIMEOUT_HINT = "`timeout` is in milliseconds: default 120000, max 600000.";

    static final String BASH_COMMAND_PARAM = "Clear, concise description of what this command does in active voice. "
            + "Never use words like \"complex\" or \"risk\" in descriptions or similar wording implying value judgments "
            + "(e.g. \"carefully\") — just describe what it does. Examples:\n"
            + "- `ls` → \"List files in current directory\"\n"
            + "- `git status` → \"Show working tree status\"\n"
            + "- `npm install` → \"Install package dependencies\"\n"
            + "- For compound commands (pipes, obscure flags), enough context to clarify what the command does: "
            + "`curl -s url | jq '.data[]'` → \"Fetch JSON from URL and extract data array elements\"";

    static final String GLOB = "Fast file pattern matching. Supports glob patterns like \"**/*.js\" or \"src/**/*.ts\". "
            + "Returns matching file paths sorted by modification time.";

    static final String GLOB_PATH_PARAM = "The directory to search in. If not specified, the current working directory "
            + "will be used. IMPORTANT: Omit this field to use the default directory. DO NOT enter \"undefined\" or "
            + "\"null\" - simply omit it for the default behavior. Must be a valid directory path if provided.";

    static final String GREP = """
            Content search built on ripgrep. Prefer this over `grep`/`rg` via Bash — results integrate with the permission UI and file links.

            - Full regex syntax (e.g. "log.*Error", "function\\s+\\w+"). Ripgrep, not grep — escape literal braces (`interface\\{\\}`).
            - Filter with `glob` (e.g. "**/*.tsx") or `type` (e.g. "js", "py", "rust").
            - `output_mode`: "content" (matching lines), "files_with_matches" (paths only, default), or "count".
            - `multiline: true` for patterns that span lines.""";

    static final String WEB_FETCH = """
            Fetches a URL, converts the page to markdown, and answers `prompt` against it using a small fast model.

            - Fails on authenticated/private URLs — use an authenticated MCP tool or `gh` for those instead.
            - HTTP is upgraded to HTTPS. Cross-host redirects are returned to you rather than followed; call again with the redirect URL.
            - Responses are cached for 15 minutes per URL.""";

    static final String WEB_SEARCH = "Search the web. Returns result blocks with titles and URLs. US-only.\n\n"
            + "- The current month is %s — use this when searching for recent information.\n"
            + "- `allowed_domains` / `blocked_domains` filter results.\n"
            + "- After answering from results, end with a \"Sources:\" list of the URLs you used as markdown links.";

    static final String TODO_READ = "Read the current session todo list";

    static final String TODO_WRITE = """
            Create and update a task list for the current session. The list is rendered to the user as your working plan.

            - Each todo has `content`, `status` ("pending" | "in_progress" | "completed"), and `priority` ("high" | "medium" | "low").
            - Send the full list each call; it replaces the previous one.
            - Keep one item `in_progress` at a time and mark it `completed` when done.""";

    static final String AGENT_HEADER = """
            Launch a new agent to handle complex, multi-step tasks. Each agent type has specific capabilities and tools available to it.

            Available agent types and the tools they have access to:
            - general-purpose: General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks. When you are searching for a keyword or file and are not confident that you will find the right match in the first few tries use this agent to perform this search. (Tools: *)
            - Explore: Read-only search agent for broad fan-out searches - when answering means sweeping many files, directories, and naming conventions and only need the conclusion, not the file dumps. It reads excerpts rather than whole files, so it locates code; it doesn't review or audit it. Specify search breadth: "medium" for moderate exploration, "very thorough" for multiple locations and naming conventions. (Tools: Bash, Glob, Grep, Read, WebFetch, WebSearch, TodoWrite)

            When using the Agent tool, specify a subagent_type parameter to select which agent type to use. If omitted, the general-purpose agent is used.

            ## When to use

            Reach for this when the task matches an available agent type, when you have independent work to run in parallel, or when answering would mean reading across several files — delegate it and you keep the conclusion, not the file dumps. For a single-fact lookup where you already know the file, symbol, or value, search directly. Once you've delegated a search, don't also run it yourself — wait for the result.

            - The agent's final message is returned to you as the tool result; it is not shown to the user — relay what matters.
            - A new Agent call starts fresh, so the prompt must be self-contained.
            - `run_in_background: true` runs the agent asynchronously; you'll be notified when it completes.
            - When you launch multiple agents for independent work, send them in a single message with multiple tool uses so they run concurrently.""";

    static final String TASK_OUTPUT = """
            - Retrieves output from a running or completed task (background shell, agent, or remote session)
            - Takes a task_id parameter identifying the task
            - Returns the task output along with status information
            - Use block=true (default) to wait for task completion
            - Use block=false for non-blocking check of current status
            - Task IDs can be found using the /tasks command
            - Works with all task types: background shells, async agents, and remote sessions""";

    static final String TASK_STOP = """
            - Stops a running background task by its ID
            - Takes a task_id parameter identifying the task to stop
            - Returns a success or failure status
            - Use this tool when you need to terminate a long-running task""";

    static final String ASK_USER_QUESTION = """
            Use this tool only when you are blocked on a decision that is genuinely the user's to make: one you cannot resolve from the request, the code, or sensible defaults.

            Usage notes:
            - Questions are single-choice on the user side: exactly one option is selected, or a free-text "Other" answer. Never promise multi-select behavior.
            - Give every option a short `key` you pick yourself (A/B/C, 1/2/3, or a compact word). Users may answer by referencing a key with additions (e.g. "A, but cheaper") — such answers mean: that option plus the stated modifications.
            - The client always offers a free-text "Other" choice automatically; do not list one yourself.
            - If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label.
            - Keep the overall question under 120 characters; labels should be lowercase except for proper nouns, acronyms, or identifiers you would normally capitalize.
            - Reserve this tool for decisions that materially change what you do next — not for choices with a conventional default or facts you can verify yourself.

            Preview feature:
            - Use the optional `preview` field on options when presenting concrete artifacts that users need to visually compare: ASCII mockups of UI layouts, code snippets showing different implementations, diagram variations, or configuration examples.
            - Preview content is rendered as markdown in a monospace box next to the option list.
            - Do not use previews for simple preference questions where labels and descriptions suffice.""";
}
