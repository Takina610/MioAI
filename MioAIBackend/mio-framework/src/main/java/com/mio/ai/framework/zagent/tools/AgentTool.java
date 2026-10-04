package com.mio.ai.framework.zagent.tools;

import com.mio.ai.framework.zagent.subagent.SubagentLauncher;

import static com.mio.ai.framework.zagent.tools.ToolDescriptions.AGENT_HEADER;

/**
 * Agent 工具（zcode handlers/agent.ts 的对位移植）：
 * 前台阻塞运行子代理（最终文本 + agentId + usage 块返回）；后台入注册表。
 */
final class AgentTool {

    private final SubagentLauncher launcher;

    AgentTool(SubagentLauncher launcher) {
        this.launcher = launcher;
    }

    ToolEntry entry() {
        String schema = """
                {"type":"object","properties":{
                "description":{"type":"string","description":"A short (3-5 word) description of the task"},
                "prompt":{"type":"string","description":"The task for the agent to perform"},
                "subagent_type":{"type":"string","description":"The type of specialized agent to use for this task"},
                "run_in_background":{"type":"boolean","description":"Set to true to run this agent in the background."}},
                "required":["description","prompt"]}""";
        return ToolEntry.ofMutable("Agent", AGENT_HEADER, schema, 0, this::execute);
    }

    String execute(com.fasterxml.jackson.databind.JsonNode input, ToolContext ctx) {
        if (ctx.subagents == null) {
            throw new ToolUseFailure(30, "Subagents are not available in this context.");
        }
        String description = Args.str(input, "description");
        String prompt = Args.str(input, "prompt");
        String type = Args.str(input, "subagent_type");
        boolean background = Args.bool(input, "run_in_background");
        if (prompt == null) {
            throw new ToolUseFailure(31, "prompt is required.");
        }
        SubagentLauncher.Result result = ctx.subagents.launch(type, description, prompt, background);
        if (result.launchedInBackground()) {
            return "Agent " + result.agentId() + " (" + result.type() + ") launched in background.\n"
                    + "task_id: " + result.backgroundTaskId() + "\n"
                    + "Briefly tell the user what you launched, then end your response; "
                    + "do not duplicate this agent's work while it runs.";
        }
        StringBuilder sb = new StringBuilder(result.content() == null || result.content().isBlank()
                ? "(Subagent completed but returned no output.)" : result.content());
        sb.append("\n\nagentId: ").append(result.agentId())
                .append(" (the agent's conversation is not resumable; launch a new Agent with full context "
                        + "if follow-up work is needed)");
        sb.append("\n<usage>subagent_tokens: ").append(result.tokens())
                .append("\ntool_uses: ").append(result.toolUseCount())
                .append("\nduration_ms: ").append(result.durationMs())
                .append("</usage>");
        return sb.toString();
    }
}
