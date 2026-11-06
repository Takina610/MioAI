package com.mio.ai.bot.agent.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.bot.service.resource.ResourceAdminService;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.framework.zagent.tools.contract.ToolEntry;
import com.mio.ai.framework.zagent.tools.contract.ToolRegistry;
import com.mio.ai.framework.zagent.tools.contract.ToolUseFailure;
import com.mio.ai.framework.zagent.tools.support.Args;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话内资源管理工具（MioBot 主轮次注册；群聊 Router 与子代理均不注册）。
 * <p>四个工具覆盖一期范围：列资源、存智能体、建群聊、绑定/解绑知识库-MCP-技能。
 * 删除类操作不提供——级联删除会连带清掉会话与沙箱记忆，对话里误触代价过大。
 * <p>业务校验与鉴权全部下沉到 {@link ResourceAdminService}：工具层只做参数取值与
 * 中文回执组织，失败统一转成 {@link ToolUseFailure} 让模型看到可自纠的原因。
 */
public final class ResourceTools {

    private static final String LIST_DESCRIPTION = """
            List the user's own resources: custom agents (domain=agent), group chats (domain=group), \
            knowledge bases (domain=knowledge), MCP servers (domain=mcp), skills (domain=skill). \
            Returns "#id name | status | description" lines. \
            You rarely need to call this explicitly — agent_save, group_create and agent_bind all accept \
            names and resolve them for you. Call it only when the user asks what they have, or when a \
            name lookup came back ambiguous and you need to show the options.""";

    private static final String LIST_SCHEMA = """
            {"type":"object","properties":{
            "domain":{"type":"string","enum":["agent","group","knowledge","mcp","skill"],"description":"Which resource category to list"},
            "query":{"type":"string","description":"Optional name filter (substring match)"}},
            "required":["domain"]}""";

    private static final String SAVE_DESCRIPTION = """
            Create or update one of the user's custom agents. \
            USE THIS WHENEVER the user describes an assistant they want to exist — e.g. "帮我建一个写代码的助手", \
            "创建一个翻译官", "把这个人设存成智能体". Do NOT ask the user for ids or JSON. \
            INFER name, description and systemPrompt from what they said and write a complete, usable persona — \
            the persona is the most important field, not a restatement of the name. \
            ICON: if the user already stated a color or look ("绿色清爽的", "紫色高级感"), just pick matching \
            shape+color and create immediately — do NOT ask. If they said nothing about appearance, \
            ask ONE question via AskUserQuestion BEFORE creating, offering 3-4 palette options, each carrying \
            shape+color so the client renders a real icon preview. Example question payload:
            questions=[{
              "question":"选个图标配色？","header":"图标",
              "options":[
                {"key":"A","label":"青绿","description":"清爽专业","shape":"hex","color":"cyan"},
                {"key":"B","label":"橙色","description":"活泼有劲","shape":"blob","color":"orange"},
                {"key":"C","label":"紫罗兰","description":"精致沉稳","shape":"gem","color":"violet"},
                {"key":"D","label":"绿色","description":"自然放松","shape":"leaf","color":"green"}
              ]}]
            Then create with the chosen shape+color. Omit agentId to create (new agents start as draft). \
            Provide agentId to update; only the fields you send change. \
            After a successful create, render a preview card with render_ui using kind="agent_card" \
            (id/name/description/status/shape/color from this tool's result) so the user can see and jump to it. \
            Do NOT invent knowledge bases, MCP tools or skills inside the prompt; bind them with agent_bind.""";

    private static final String SAVE_SCHEMA = """
            {"type":"object","properties":{
            "agentId":{"type":"integer","description":"Existing agent id to update; omit to create a new agent"},
            "name":{"type":"string","maxLength":100,"description":"Agent name (required when creating). Derive from the user's words; keep it short."},
            "description":{"type":"string","maxLength":2000,"description":"One-line description shown in the dashboard. Summarize what it does."},
            "systemPrompt":{"type":"string","maxLength":10000,"description":"The agent's persona / system prompt, written out in full from the user's intent"},
            "shape":{"type":"string","enum":["blob","squircle","hex","gem","cloud","teardrop","capsule","leaf"],"description":"Icon body shape. blob=团子, squircle=方圆, hex=六角, gem=宝石, cloud=云朵, teardrop=水滴, capsule=胶囊, leaf=叶片. Pick one that fits the agent's character; omit for blob."},
            "color":{"type":"string","enum":["cyan","blue","violet","green","orange","yellow","red","magenta","brown","gray","black"],"description":"Icon palette color. Pick one matching the agent's vibe (cyan=clean/technical, orange=energetic, violet=premium, green=natural). Omit for black."}},
            "required":[]}""";

    private static final String GROUP_DESCRIPTION = """
            Create a group chat that dispatches work across the user's custom agents. \
            USE THIS WHENEVER the user names several agents and wants them to collaborate, \
            e.g. "用美食推荐官和翻译专家建个群", "把这几个专家组一个团队". \
            This tool ACTUALLY CREATES the group and returns its real numeric id — \
            you must call it before rendering any group card. Do NOT draw an agent_card for a group \
            you have not created: invented ids make the card's buttons dead. \
            memberAgentNames accepts agent NAMES as the user said them — resolution is automatic, \
            so you do not need to call resource_list first. Pass memberAgentIds only if you already have ids. \
            Members must be 2-10 of the user's OWN custom agents (built-in agents and groups are rejected). \
            After creation, render ONE preview card with render_ui, kind="agent_card", target="group", \
            using the returned id and the members list from this tool's result.""";

    private static final String GROUP_SCHEMA = """
            {"type":"object","properties":{
            "name":{"type":"string","maxLength":32,"description":"Group name. Derive a short fitting name if the user did not give one."},
            "memberAgentNames":{"type":"array","minItems":2,"maxItems":10,"items":{"type":"string"},"description":"Member agent NAMES as the user said them (2-10). Resolved automatically; ambiguous names report the candidates."},
            "memberAgentIds":{"type":"array","minItems":2,"maxItems":10,"items":{"type":"integer"},"description":"Member agent ids, only when already known"},
            "description":{"type":"string","maxLength":2000,"description":"Optional group description"}},
            "required":["name"]}""";

    private static final String BIND_DESCRIPTION = """
            Attach or detach a knowledge base, MCP server or skill to one of the user's custom agents. \
            USE THIS WHENEVER the user says to connect/挂载/绑定 something to an agent. \
            agentName and resourceName accept the NAMES the user said — resolution is automatic \
            (exact match wins, then case-insensitive, then a unique substring), so you do not need ids first. \
            action=bind attaches, action=unbind detaches. For knowledge you may also set topK (1-50), \
            threshold (0-1) and method (hybrid|vector); omitted values keep defaults (topK 5, threshold 0.7, hybrid). \
            Binding is idempotent: an already-bound pair reports "already bound" instead of failing. \
            Never operate on a group chat, a built-in agent or someone else's agent.""";

    private static final String BIND_SCHEMA = """
            {"type":"object","properties":{
            "action":{"type":"string","enum":["bind","unbind"],"description":"bind attaches, unbind detaches"},
            "agentName":{"type":"string","description":"Target agent NAME as the user said it (preferred)"},
            "agentId":{"type":"integer","description":"Target agent id, only when already known"},
            "domain":{"type":"string","enum":["knowledge","mcp","skill"],"description":"Binding category"},
            "resourceName":{"type":"string","description":"Knowledge base / MCP / skill NAME as the user said it (preferred)"},
            "resourceId":{"type":"integer","description":"Knowledge base / MCP / skill id, only when already known"},
            "topK":{"type":"integer","minimum":1,"maximum":50,"description":"knowledge only: max chunks retrieved"},
            "threshold":{"type":"number","minimum":0,"maximum":1,"description":"knowledge only: similarity threshold"},
            "method":{"type":"string","enum":["hybrid","vector"],"description":"knowledge only: retrieval method"},
            "enabled":{"type":"integer","enum":[0,1],"description":"1 to enable the binding (default), 0 to keep it disabled"}},
            "required":["action","domain"]}""";

    private ResourceTools() {
    }

    /**
     * 注册全部资源管理工具。调用方负责判定主轮次与登录态——这里不再重复检查。
     */
    public static void registerAll(ToolRegistry registry, ResourceAdminService service, Long userId) {
        if (registry == null || service == null || userId == null) {
            return;
        }
        registry.register(listEntry(service, userId));
        registry.register(saveEntry(service, userId));
        registry.register(groupEntry(service, userId));
        registry.register(bindEntry(service, userId));
    }

    static ToolEntry listEntry(ResourceAdminService service, Long userId) {
        ToolEntry.StringHandler handler = (input, ctx) -> guarded(() ->
                service.list(userId, Args.str(input, "domain"), Args.str(input, "query")));
        return ToolEntry.ofReadOnly("resource_list", LIST_DESCRIPTION, LIST_SCHEMA, handler);
    }

    static ToolEntry saveEntry(ResourceAdminService service, Long userId) {
        ToolEntry.StringHandler handler = (input, ctx) -> guarded(() ->
                service.saveAgent(userId, longVal(input, "agentId"), Args.str(input, "name"),
                        Args.str(input, "description"), Args.str(input, "systemPrompt"),
                        Args.str(input, "shape"), Args.str(input, "color")));
        return ToolEntry.ofMutable("agent_save", SAVE_DESCRIPTION, SAVE_SCHEMA, 60_000, handler);
    }

    static ToolEntry groupEntry(ResourceAdminService service, Long userId) {
        ToolEntry.StringHandler handler = (input, ctx) -> guarded(() -> {
            List<Long> memberIds = longList(input, "memberAgentIds");
            if (memberIds == null || memberIds.isEmpty()) {
                // 名字优先：用户怎么说就怎么传，服务层按完全同名→忽略大小写→唯一包含匹配解析
                memberIds = resolveNames(service, userId, stringList(input, "memberAgentNames"),
                        "智能体");
            }
            return service.createGroup(userId, Args.str(input, "name"), memberIds,
                    Args.str(input, "description"));
        });
        return ToolEntry.ofMutable("group_create", GROUP_DESCRIPTION, GROUP_SCHEMA, 60_000, handler);
    }

    static ToolEntry bindEntry(ResourceAdminService service, Long userId) {
        ToolEntry.StringHandler handler = (input, ctx) -> guarded(() -> {
            String action = Args.str(input, "action");
            boolean bind;
            if ("bind".equalsIgnoreCase(action)) {
                bind = true;
            } else if ("unbind".equalsIgnoreCase(action)) {
                bind = false;
            } else {
                throw new ToolUseFailure(1, "action 只能是 bind 或 unbind");
            }
            String domain = Args.str(input, "domain");
            Long agentId = longVal(input, "agentId");
            if (agentId == null) {
                agentId = resolveOne(service, userId, Args.str(input, "agentName"),
                        service::resolveAgentIdByName, "智能体");
            }
            Long resourceId = longVal(input, "resourceId");
            if (resourceId == null) {
                String resourceName = Args.str(input, "resourceName");
                resourceId = resolveOne(service, userId, resourceName,
                        (uid, name) -> service.resolveResourceIdByName(uid, domain, name), domainLabel(domain));
            }
            if (agentId == null) {
                throw new ToolUseFailure(6, "请告诉我要操作哪个智能体（agentName 或 agentId）");
            }
            if (resourceId == null) {
                throw new ToolUseFailure(7, "请告诉我要" + (bind ? "绑定" : "解绑")
                        + "哪个" + domainLabel(domain) + "（resourceName 或 resourceId）");
            }
            ResourceAdminService.RetrievalOptions retrieval =
                    new ResourceAdminService.RetrievalOptions(
                            Args.intVal(input, "topK"), doubleVal(input, "threshold"),
                            Args.str(input, "method"));
            return service.bind(userId, bind, agentId, domain, resourceId,
                    retrieval, Args.intVal(input, "enabled"));
        });
        return ToolEntry.ofMutable("agent_bind", BIND_DESCRIPTION, BIND_SCHEMA, 60_000, handler);
    }

    /** 名字列表 → id 列表；任何一个解析不出来都报错（不静默丢弃成员） */
    private static List<Long> resolveNames(ResourceAdminService service, Long userId,
                                          List<String> names, String label) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (String name : names) {
            Long id = service.resolveAgentIdByName(userId, name);
            if (id == null) {
                throw new ToolUseFailure(8, "找不到名为「" + name + "」的" + label
                        + "。可用 resource_list(domain=agent) 看看有哪些，或直接给 id");
            }
            ids.add(id);
        }
        return ids;
    }

    private static Long resolveOne(ResourceAdminService service, Long userId, String name,
                                   java.util.function.BiFunction<Long, String, Long> resolver,
                                   String label) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Long id = resolver.apply(userId, name);
        if (id == null) {
            throw new ToolUseFailure(9, "找不到名为「" + name + "」的" + label);
        }
        return id;
    }

    private static String domainLabel(String domain) {
        return switch (domain == null ? "" : domain.toLowerCase()) {
            case "knowledge" -> "知识库";
            case "mcp" -> "MCP";
            case "skill" -> "技能";
            default -> "资源";
        };
    }

    /** 业务异常转工具错误信封：模型能看到中文原因并自纠，轮次不中断 */
    private static String guarded(java.util.function.Supplier<String> action) {
        try {
            return action.get();
        } catch (BusinessException e) {
            throw new ToolUseFailure(e.getCode(), e.getMessage());
        }
    }

    private static Long longVal(JsonNode input, String field) {
        if (input == null || !input.hasNonNull(field)) {
            return null;
        }
        JsonNode node = input.get(field);
        if (node.isNumber()) {
            return node.asLong();
        }
        String raw = Args.cleanPath(node.asText());
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new ToolUseFailure(2, field + " 必须是数字：" + raw);
        }
    }

    private static Double doubleVal(JsonNode input, String field) {
        if (input == null || !input.hasNonNull(field)) {
            return null;
        }
        JsonNode node = input.get(field);
        if (node.isNumber()) {
            return node.asDouble();
        }
        String raw = Args.cleanPath(node.asText());
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new ToolUseFailure(3, field + " 必须是数字：" + raw);
        }
    }

    private static List<String> stringList(JsonNode input, String field) {
        if (input == null || !input.hasNonNull(field)) {
            return null;
        }
        JsonNode node = input.get(field);
        if (!node.isArray()) {
            return List.of(node.asText());
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String raw = Args.cleanPath(item.asText());
            if (raw != null) {
                values.add(raw);
            }
        }
        return values;
    }

    private static List<Long> longList(JsonNode input, String field) {
        if (input == null || !input.hasNonNull(field)) {
            return null;
        }
        JsonNode node = input.get(field);
        if (!node.isArray()) {
            // 宽容归一：模型把成员 id 传成逗号分隔字符串时按列表解析，避免整轮失败重试
            return parseIdList(node.asText());
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isNumber()) {
                ids.add(item.asLong());
                continue;
            }
            String raw = Args.cleanPath(item.asText());
            if (raw == null) {
                continue;
            }
            ids.add(parseId(raw, field));
        }
        return ids;
    }

    private static List<Long> parseIdList(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split("[,，\\s]+")) {
            if (!part.isBlank()) {
                ids.add(parseId(part, "memberAgentIds"));
            }
        }
        return ids;
    }

    private static Long parseId(String raw, String field) {
        String cleaned = Args.cleanPath(raw);
        if (cleaned == null) {
            return null;
        }
        try {
            return Long.parseLong(cleaned.trim());
        } catch (NumberFormatException e) {
            throw new ToolUseFailure(5, field + " 里的 " + raw + " 不是有效 id");
        }
    }
}
