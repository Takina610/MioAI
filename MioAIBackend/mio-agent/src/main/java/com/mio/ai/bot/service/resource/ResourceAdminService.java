package com.mio.ai.bot.service.resource;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.XssUtils;
import com.mio.ai.framework.rag.retrieval.RetrievalConfig;
import com.mio.ai.resource.model.dto.agent.AgentAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentGroupCreateRequest;
import com.mio.ai.resource.model.dto.agent.AgentKnowledgeAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentMcpAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentQueryRequest;
import com.mio.ai.resource.model.dto.agent.AgentUpdateRequest;
import com.mio.ai.resource.model.dto.knowledge.KnowledgeBaseQueryRequest;
import com.mio.ai.resource.model.dto.mcp.McpToolQueryRequest;
import com.mio.ai.resource.model.dto.skill.SkillQueryRequest;
import com.mio.ai.resource.model.entity.agent.Agent;
import com.mio.ai.resource.model.entity.agent.AgentSkill;
import com.mio.ai.resource.model.enums.agent.AgentStatusEnum;
import com.mio.ai.resource.model.enums.agent.AgentTypeEnum;
import com.mio.ai.resource.model.vo.agent.AgentVO;
import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;
import com.mio.ai.resource.model.vo.mcp.McpToolVO;
import com.mio.ai.resource.model.vo.skill.SkillVO;
import com.mio.ai.resource.service.agent.AgentGroupService;
import com.mio.ai.resource.service.agent.AgentKnowledgeService;
import com.mio.ai.resource.service.agent.AgentMcpService;
import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.mcp.McpToolService;
import com.mio.ai.resource.service.security.AccessGuardService;
import com.mio.ai.resource.service.skill.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话内资源管理（MioBot 主轮次内置工具 resource_* 的落地层）。
 * <p>写方法绕过了 controller 的 {@code @Valid} 与 @XssClean：鉴权、文本清洗、上限校验
 * 都必须在这里补齐——服务层 add/update 本身不做授权（校验原本在 controller）。
 */
@Slf4j
@Service
public class ResourceAdminService {

    public static final String DOMAIN_AGENT = "agent";
    public static final String DOMAIN_GROUP = "group";
    public static final String DOMAIN_KNOWLEDGE = "knowledge";
    public static final String DOMAIN_MCP = "mcp";
    public static final String DOMAIN_SKILL = "skill";

    private static final int LIST_LIMIT = 50;
    /** 列表行内描述截断长度（完整内容在详情页，这里只要可读摘要） */
    private static final int LIST_DESC_LENGTH = 60;

    private static final int MAX_NAME = 100;
    private static final int MAX_DESCRIPTION = 2000;
    private static final int MAX_SYSTEM_PROMPT = 10000;

    /** 绑定知识库时可选覆盖的检索参数（缺省沿用 RetrievalConfig 默认值） */
    public record RetrievalOptions(Integer topK, Double threshold, String method) {
    }

    private final AccessGuardService accessGuardService;
    private final AgentService agentService;
    private final AgentGroupService agentGroupService;
    private final AgentKnowledgeService agentKnowledgeService;
    private final AgentMcpService agentMcpService;
    private final AgentSkillService agentSkillService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final McpToolService mcpToolService;
    private final SkillService skillService;

    public ResourceAdminService(AccessGuardService accessGuardService,
                                AgentService agentService,
                                AgentGroupService agentGroupService,
                                AgentKnowledgeService agentKnowledgeService,
                                AgentMcpService agentMcpService,
                                AgentSkillService agentSkillService,
                                KnowledgeBaseService knowledgeBaseService,
                                McpToolService mcpToolService,
                                SkillService skillService) {
        this.accessGuardService = accessGuardService;
        this.agentService = agentService;
        this.agentGroupService = agentGroupService;
        this.agentKnowledgeService = agentKnowledgeService;
        this.agentMcpService = agentMcpService;
        this.agentSkillService = agentSkillService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.mcpToolService = mcpToolService;
        this.skillService = skillService;
    }

    /**
     * 列出当前用户可装配的资源。domain 决定查询域，query 为名称模糊过滤。
     */
    public String list(Long userId, String domain, String query) {
        requireLogin(userId);
        String nameFilter = cleanIfPresent(query, "query", MAX_NAME);
        return switch (normalizeDomain(domain)) {
            case DOMAIN_AGENT -> listAgents(userId, AgentTypeEnum.CUSTOM.getCode(), nameFilter);
            case DOMAIN_GROUP -> listAgents(userId, AgentTypeEnum.GROUP.getCode(), nameFilter);
            case DOMAIN_KNOWLEDGE -> listKnowledge(userId, nameFilter);
            case DOMAIN_MCP -> listMcp(userId, nameFilter);
            case DOMAIN_SKILL -> listSkills(userId, nameFilter);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知的资源类型：" + domain);
        };
    }

    /**
     * 创建或更新自定义智能体。agentId 为空即创建；更新时只覆盖显式给出的字段。
     * <p>图标走 {@code shape}+{@code color} 结构化参数，不接受模型自拼 JSON——
     * agent.icon 无后端校验，写错时前端静默回退默认图标，用户只会觉得"设置没生效"。
     */
    public String saveAgent(Long userId, Long agentId, String name, String description,
                            String systemPrompt, String shape, String color) {
        requireLogin(userId);
        String cleanName = cleanIfPresent(name, "name", MAX_NAME);
        String cleanDesc = cleanIfPresent(description, "description", MAX_DESCRIPTION);
        String cleanPrompt = cleanIfPresent(systemPrompt, "systemPrompt", MAX_SYSTEM_PROMPT);
        String icon;
        try {
            icon = shape == null && color == null ? null : AgentIconSpec.toJson(shape, color);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, e.getMessage());
        }

        if (agentId == null) {
            if (cleanName == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建智能体必须提供 name");
            }
            AgentAddRequest request = new AgentAddRequest();
            request.setName(cleanName);
            request.setDescription(cleanDesc);
            request.setSystemPrompt(cleanPrompt);
            request.setIcon(icon);
            Long newId = agentService.addAgent(request, userId);
            String resolvedShape = shape == null || shape.isBlank() ? AgentIconSpec.DEFAULT_SHAPE : shape.trim();
            String resolvedColor = color == null || color.isBlank() ? AgentIconSpec.DEFAULT_COLOR : color.trim();
            String resolvedIcon = AgentIconSpec.toJson(resolvedShape, resolvedColor);
            // 回执带上卡片所需的全部字段：模型据此渲染 agent_card，不必再查一次。
            // shape/color 一律回填"最终生效值"——模型漏传时会用默认值，
            // 若卡片仍按自己想的颜色渲染，界面上看到的和库里存的就不是一个东西。
            return "已创建智能体 #" + newId + "「" + cleanName + "」（草稿状态，图标 "
                    + AgentIconSpec.describe(resolvedShape, resolvedColor) + "）。\n"
                    + "现在用 render_ui 渲染预览卡片，kind=\"agent_card\"，参数（原样照抄，不要自己改颜色）：\n"
                    + "  id=" + newId + ", name=" + cleanName
                    + ", status=草稿, target=agent"
                    + (cleanDesc != null ? ", description=" + cleanDesc : "")
                    + ", shape=" + resolvedShape + ", color=" + resolvedColor + "\n"
                    + "卡片会带「开始对话」「编辑配置」两个跳转按钮。";
        }

        requireCustomAgent(agentId, userId);
        if (cleanName == null && cleanDesc == null && cleanPrompt == null && icon == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "没有可更新的字段");
        }
        AgentUpdateRequest request = new AgentUpdateRequest();
        request.setId(agentId);
        request.setName(cleanName);
        request.setDescription(cleanDesc);
        request.setSystemPrompt(cleanPrompt);
        request.setIcon(icon);
        agentService.updateAgent(request, userId);
        return "已更新智能体 #" + agentId + "。";
    }

    /**
     * 读取刚创建/更新的智能体快照，供工具生成预览卡片（agent_card 块）。
     * <p>只暴露卡片需要的字段：名称、描述、图标、状态、类型。走 checkAgentOwner，
     * 调用前已通过 saveAgent 的鉴权，这里再确认一次归属防止 id 被换掉。
     */
    public AgentCard card(Long userId, Long agentId) {
        requireLogin(userId);
        Agent agent = accessGuardService.checkAgentOwner(agentId, userId);
        return new AgentCard(
                agent.getId(),
                agent.getName(),
                agent.getDescription() == null ? "" : agent.getDescription(),
                agent.getIcon() == null ? "" : agent.getIcon(),
                statusText(agent.getStatus()),
                isCustom(agent) ? "custom" : "other");
    }

    /** 预览卡片数据（agent_card 块的模型侧来源） */
    public record AgentCard(Long id, String name, String description, String icon,
                            String status, String kind) {
    }

    /** 群成员快照：给 agent_card 的 members 用（前端 GroupAvatar 堆叠渲染） */
    public record GroupMember(String id, String name, String icon) {
    }

    /**
     * 读群成员（id/名称/图标 JSON），供创建后渲染堆叠头像。
     * <p>成员图标可能为空（老数据未设图标）：前端 GroupAvatar 会回退到首字方块。
     */
    public List<GroupMember> groupMembers(Long userId, Long groupId) {
        requireLogin(userId);
        accessGuardService.checkAgentOwner(groupId, userId);
        return agentGroupService.listMembers(groupId).stream()
                .map(agent -> new GroupMember(
                        String.valueOf(agent.getId()),
                        agent.getName(),
                        agent.getIcon() == null ? "" : agent.getIcon()))
                .toList();
    }

    /** 把群成员转成模型可读的参数行（含各自的 shape/color，便于原样传给 render_ui） */
    private String groupMembersHint(Long groupId) {
        List<GroupMember> members;
        try {
            members = agentGroupService.listMembers(groupId).stream()
                    .map(agent -> new GroupMember(String.valueOf(agent.getId()), agent.getName(),
                            agent.getIcon() == null ? "" : agent.getIcon()))
                    .toList();
        } catch (Exception e) {
            return "";
        }
        if (members.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("  members=[");
        for (int i = 0; i < members.size(); i++) {
            GroupMember m = members.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append("{id:").append(m.id()).append(", name:").append(m.name());
            String icon = m.icon();
            if (icon != null && !icon.isBlank()) {
                String shape = shapeOf(icon);
                String color = colorOf(icon);
                if (shape != null) {
                    sb.append(", shape:").append(shape);
                }
                if (color != null) {
                    sb.append(", color:").append(color);
                }
            }
            sb.append('}');
        }
        return sb.append("]\n").toString();
    }

    /** 从成员 icon JSON 里取 shape（老数据/异常格式返回 null，交给前端回退首字） */
    private static String shapeOf(String iconJson) {
        String shape = cn.hutool.json.JSONUtil.parseObj(iconJson).getStr("shape", null);
        return AgentIconSpec.isValidShape(shape) ? shape : null;
    }

    /** 从成员 icon JSON 里取 palette 色 id */
    private static String colorOf(String iconJson) {
        cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(iconJson);
        cn.hutool.json.JSONObject fill = obj.getJSONObject("fill");
        if (fill == null || !"palette".equals(fill.getStr("kind"))) {
            return null;
        }
        String id = fill.getStr("id", null);
        return AgentIconSpec.isValidColor(id) ? id : null;
    }

    private static boolean isCustom(Agent agent) {
        return Integer.valueOf(AgentTypeEnum.CUSTOM.getCode()).equals(agent.getType());
    }

    /**
     * 名字 → 智能体 id（用户说"用美食推荐官建群"时工具不必逼模型先查列表）。
     * <p>匹配顺序：完全同名 → 忽略大小写同名 → 唯一的包含匹配。多个候选时报错并列出
     * 可选的 id，让模型自己澄清，而不是随便挑一个建错群。
     */
    public Long resolveAgentIdByName(Long userId, String name) {
        requireLogin(userId);
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim();
        return pickByName(wanted, customAgentsNamed(userId, wanted),
                AgentVO::getId, AgentVO::getName);
    }

    /** 按名字模糊查本人的自定义智能体（供 resolveAgentIdByName 与错误提示复用） */
    private List<AgentVO> customAgentsNamed(Long userId, String name) {
        AgentQueryRequest request = new AgentQueryRequest();
        request.setUserId(userId);
        request.setType(AgentTypeEnum.CUSTOM.getCode());
        request.setName(name);
        request.setPageSize(LIST_LIMIT);
        return agentService.queryAgents(request).getRecords();
    }

    /**
     * 名字 → 绑定目标 id（知识库/MCP/技能）。匹配规则与 {@link #resolveAgentIdByName} 一致：
     * 完全同名 → 忽略大小写 → 唯一包含匹配，多候选报错并列出。
     */
    public Long resolveResourceIdByName(Long userId, String domain, String name) {
        requireLogin(userId);
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim();
        return switch (normalizeDomain(domain)) {
            case DOMAIN_KNOWLEDGE -> pickByName(wanted, namedKbs(userId, wanted),
                    KnowledgeBaseVO::getId, KnowledgeBaseVO::getName);
            case DOMAIN_MCP -> pickByName(wanted, namedMcps(userId, wanted),
                    McpToolVO::getId, McpToolVO::getName);
            case DOMAIN_SKILL -> pickByName(wanted, namedSkills(userId, wanted),
                    SkillVO::getId, SkillVO::getName);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "未知的资源类型：" + domain);
        };
    }

    /** 通用同名优先挑选：完全同名 > 忽略大小写 > 唯一包含匹配 */
    private <T> Long pickByName(String wanted, List<T> candidates,
                                java.util.function.Function<T, Long> idOf,
                                java.util.function.Function<T, String> nameOf) {
        if (candidates.isEmpty()) {
            return null;
        }
        for (T item : candidates) {
            if (wanted.equals(nameOf.apply(item))) {
                return idOf.apply(item);
            }
        }
        for (T item : candidates) {
            if (wanted.equalsIgnoreCase(nameOf.apply(item))) {
                return idOf.apply(item);
            }
        }
        if (candidates.size() == 1) {
            return idOf.apply(candidates.get(0));
        }
        String list = candidates.stream()
                .map(item -> "#" + idOf.apply(item) + "「" + nameOf.apply(item) + "」")
                .collect(java.util.stream.Collectors.joining("、"));
        throw new BusinessException(ErrorCode.PARAMS_ERROR,
                "有 " + candidates.size() + " 个资源匹配「" + wanted + "」：" + list + "，请指明是哪一个");
    }

    private List<KnowledgeBaseVO> namedKbs(Long userId, String name) {
        KnowledgeBaseQueryRequest request = new KnowledgeBaseQueryRequest();
        request.setUserId(userId);
        request.setName(name);
        request.setPageSize(LIST_LIMIT);
        return knowledgeBaseService.queryKnowledgeBases(request).getRecords();
    }

    private List<McpToolVO> namedMcps(Long userId, String name) {
        McpToolQueryRequest request = new McpToolQueryRequest();
        request.setUserId(userId);
        request.setName(name);
        request.setPageSize(LIST_LIMIT);
        return mcpToolService.queryMcpTools(request).getRecords();
    }

    private List<SkillVO> namedSkills(Long userId, String name) {
        SkillQueryRequest request = new SkillQueryRequest();
        request.setUserId(userId);
        request.setName(name);
        request.setPageSize(LIST_LIMIT);
        return skillService.querySkills(request).getRecords();
    }

    /**
     * 创建群聊：成员必须是本人 2..10 个自定义智能体（规则由 AgentGroupServiceImpl 裁定）。
     */
    public String createGroup(Long userId, String name, List<Long> memberAgentIds, String description) {
        requireLogin(userId);
        String cleanName = cleanIfPresent(name, "name", MAX_NAME);
        if (cleanName == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建群聊必须提供 name");
        }
        if (memberAgentIds == null || memberAgentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建群聊必须提供 memberAgentIds");
        }
        AgentGroupCreateRequest request = new AgentGroupCreateRequest();
        request.setName(cleanName);
        request.setDescription(cleanIfPresent(description, "description", MAX_DESCRIPTION));
        request.setMemberAgentIds(memberAgentIds);
        Long groupId = agentGroupService.createGroup(request, userId);
        int memberCount = (int) memberAgentIds.stream().distinct().count();
        return "已创建群聊 #" + groupId + "「" + cleanName + "」，成员 " + memberCount + " 个。\n"
                + "现在用 render_ui 渲染预览卡片，kind=\"agent_card\"，参数：\n"
                + "  id=" + groupId + ", name=" + cleanName + ", status=已发布, target=group\n"
                + groupMembersHint(groupId)
                + "群聊头像由成员图标自动堆叠生成（2 个左右、3 个上中+下二、4 个满格、5+ 省略号），"
                + "不要给它传 shape/color。\n"
                + "群聊卡片只带「开始对话」按钮（群聊在对话内编辑）。";
    }

    /**
     * 绑定/解绑智能体与知识库、MCP、技能。重复绑定按"已绑定"回报，不依赖唯一索引报错。
     */
    public String bind(Long userId, boolean bind, Long agentId, String domain, Long resourceId,
                       RetrievalOptions retrieval, Integer enabled) {
        requireLogin(userId);
        if (agentId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "必须提供 agentId");
        }
        if (resourceId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "必须提供 resourceId");
        }
        requireCustomAgent(agentId, userId);
        int flag = enabled != null && enabled == 0 ? 0 : 1;
        String target = normalizeDomain(domain);
        return switch (target) {
            case DOMAIN_KNOWLEDGE -> bindKnowledge(userId, bind, agentId, resourceId, retrieval, flag);
            case DOMAIN_MCP -> bindMcp(userId, bind, agentId, resourceId, flag);
            case DOMAIN_SKILL -> bindSkill(userId, bind, agentId, resourceId, flag);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "agent_bind 只支持 knowledge / mcp / skill，收到：" + domain);
        };
    }

    // ---------- 绑定分支 ----------

    private String bindKnowledge(Long userId, boolean bind, Long agentId, Long kbId,
                                 RetrievalOptions retrieval, int enabled) {
        KnowledgeBaseVO kb = accessibleKb(userId, kbId);
        if (!bind) {
            boolean removed = agentKnowledgeService.deleteByAgentIdAndKbId(agentId, kbId);
            return removed ? "已解绑知识库 #" + kbId + "「" + kb.getName() + "」。"
                    : "智能体 #" + agentId + " 本来就没有绑定知识库 #" + kbId + "。";
        }
        if (agentKnowledgeService.getKbIdsByAgentId(agentId).contains(kbId)) {
            return "智能体 #" + agentId + " 已绑定知识库 #" + kbId + "「" + kb.getName() + "」，未重复添加。";
        }
        RetrievalConfig config = retrievalConfig(retrieval);
        AgentKnowledgeAddRequest request = new AgentKnowledgeAddRequest();
        request.setAgentId(agentId);
        request.setKbId(kbId);
        request.setRetrievalConfig(JSONUtil.toJsonStr(config));
        request.setEnabled(enabled);
        Long bindingId = agentKnowledgeService.addAgentKnowledge(request);
        return "已为智能体 #" + agentId + " 绑定知识库 #" + kbId + "「" + kb.getName() + "」（绑定 #"
                + bindingId + "，" + describe(config) + "）。";
    }

    private String bindMcp(Long userId, boolean bind, Long agentId, Long mcpId, int enabled) {
        McpToolVO tool = accessibleMcp(userId, mcpId);
        if (!bind) {
            boolean removed = agentMcpService.deleteByAgentIdAndMcpId(agentId, mcpId);
            return removed ? "已解绑 MCP #" + mcpId + "「" + tool.getName() + "」。"
                    : "智能体 #" + agentId + " 本来就没有绑定 MCP #" + mcpId + "。";
        }
        if (agentMcpService.getMcpIdsByAgentId(agentId).contains(mcpId)) {
            return "智能体 #" + agentId + " 已绑定 MCP #" + mcpId + "「" + tool.getName() + "」，未重复添加。";
        }
        AgentMcpAddRequest request = new AgentMcpAddRequest();
        request.setAgentId(agentId);
        request.setMcpId(mcpId);
        request.setEnabled(enabled);
        Long bindingId = agentMcpService.addAgentMcp(request, userId);
        return "已为智能体 #" + agentId + " 绑定 MCP #" + mcpId + "「" + tool.getName()
                + "」（绑定 #" + bindingId + "，沿用该 MCP 自身配置）。";
    }

    private String bindSkill(Long userId, boolean bind, Long agentId, Long skillId, int enabled) {
        SkillVO skill = accessibleSkill(userId, skillId);
        if (!bind) {
            boolean removed = agentSkillService.deleteByAgentIdAndSkillId(agentId, skillId);
            return removed ? "已解绑技能 #" + skillId + "「" + skill.getName() + "」。"
                    : "智能体 #" + agentId + " 本来就没有绑定技能 #" + skillId + "。";
        }
        AgentSkill binding = new AgentSkill();
        binding.setAgentId(agentId);
        binding.setSkillId(skillId);
        binding.setEnabled(enabled);
        Long bindingId = agentSkillService.addAgentSkill(binding);
        return "已为智能体 #" + agentId + " 绑定技能 #" + skillId + "「" + skill.getName()
                + "」（绑定 #" + bindingId + "）。";
    }

    // ---------- 列表分支 ----------

    private String listAgents(Long userId, int type, String nameFilter) {
        AgentQueryRequest request = new AgentQueryRequest();
        request.setUserId(userId);
        request.setType(type);
        request.setName(nameFilter);
        request.setPageSize(LIST_LIMIT);
        Page<AgentVO> page = agentService.queryAgents(request);
        String label = type == AgentTypeEnum.GROUP.getCode() ? "群聊" : "智能体";
        List<AgentVO> records = page.getRecords();
        if (records.isEmpty()) {
            return "没有找到符合条件的" + label + "。";
        }
        StringBuilder sb = new StringBuilder(label).append("共 ").append(page.getTotal()).append(" 个");
        sb.append(page.getTotal() > records.size() ? "（列出前 " + records.size() + " 个）" : "").append("：\n");
        for (AgentVO agent : records) {
            sb.append("- #").append(agent.getId()).append(' ').append(agent.getName())
                    .append(" | ").append(statusText(agent.getStatus()));
            if (agent.getDescription() != null && !agent.getDescription().isBlank()) {
                sb.append(" | ").append(truncate(agent.getDescription()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String listKnowledge(Long userId, String nameFilter) {
        KnowledgeBaseQueryRequest request = new KnowledgeBaseQueryRequest();
        request.setUserId(userId);
        request.setName(nameFilter);
        request.setPageSize(LIST_LIMIT);
        Page<KnowledgeBaseVO> page = knowledgeBaseService.queryKnowledgeBases(request);
        List<KnowledgeBaseVO> records = page.getRecords();
        if (records.isEmpty()) {
            return "没有找到符合条件的知识库。";
        }
        StringBuilder sb = new StringBuilder("知识库共 ").append(page.getTotal()).append(" 个：\n");
        for (KnowledgeBaseVO kb : records) {
            sb.append("- #").append(kb.getId()).append(' ').append(kb.getName())
                    .append(" | 文档 ").append(kb.getDocumentCount() == null ? 0 : kb.getDocumentCount()).append(" 个");
            if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                sb.append(" | ").append(truncate(kb.getDescription()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String listMcp(Long userId, String nameFilter) {
        McpToolQueryRequest request = new McpToolQueryRequest();
        request.setUserId(userId);
        request.setName(nameFilter);
        request.setPageSize(LIST_LIMIT);
        Page<McpToolVO> page = mcpToolService.queryMcpTools(request);
        List<McpToolVO> records = page.getRecords();
        if (records.isEmpty()) {
            return "没有找到符合条件的 MCP 工具。";
        }
        StringBuilder sb = new StringBuilder("MCP 工具共 ").append(page.getTotal()).append(" 个：\n");
        for (McpToolVO tool : records) {
            // 只输出可识别信息：config 含密钥/命令，不进模型上下文
            sb.append("- #").append(tool.getId()).append(' ').append(tool.getName())
                    .append(" | ").append(tool.getStatus() != null && tool.getStatus() == 1 ? "已启用" : "未启用");
            if (tool.getDescription() != null && !tool.getDescription().isBlank()) {
                sb.append(" | ").append(truncate(tool.getDescription()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String listSkills(Long userId, String nameFilter) {
        SkillQueryRequest request = new SkillQueryRequest();
        request.setUserId(userId);
        request.setName(nameFilter);
        request.setPageSize(LIST_LIMIT);
        Page<SkillVO> page = skillService.querySkills(request);
        List<SkillVO> records = page.getRecords();
        if (records.isEmpty()) {
            return "没有找到符合条件的技能。";
        }
        StringBuilder sb = new StringBuilder("技能共 ").append(page.getTotal()).append(" 个：\n");
        for (SkillVO skill : records) {
            sb.append("- #").append(skill.getId()).append(' ').append(skill.getName())
                    .append(" | ").append(skill.getStatusDesc() == null ? "未知状态" : skill.getStatusDesc());
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                sb.append(" | ").append(truncate(skill.getDescription()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------- 校验与格式化 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "资源管理需要登录后再使用");
        }
    }

    /** 绑定的写操作只允许落在本人的自定义智能体上（群聊/内置/个人 MioBot 不参与装配编辑） */
    private Agent requireCustomAgent(Long agentId, Long userId) {
        Agent agent = accessGuardService.checkAgentOwner(agentId, userId);
        if (!Integer.valueOf(AgentTypeEnum.CUSTOM.getCode()).equals(agent.getType())) {
            String typeDesc = AgentTypeEnum.getByCode(agent.getType() == null ? -1 : agent.getType()) == null
                    ? "未知类型" : AgentTypeEnum.getByCode(agent.getType()).getDesc();
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "只能操作自定义智能体，智能体 #" + agentId + " 是" + typeDesc);
        }
        return agent;
    }

    private KnowledgeBaseVO accessibleKb(Long userId, Long kbId) {
        accessGuardService.checkKbReadable(kbId, userId);
        return knowledgeBaseService.getKnowledgeBaseById(kbId);
    }

    private McpToolVO accessibleMcp(Long userId, Long mcpId) {
        accessGuardService.checkMcpReadable(mcpId, userId);
        return mcpToolService.getMcpToolById(mcpId, userId, false);
    }

    private SkillVO accessibleSkill(Long userId, Long skillId) {
        accessGuardService.checkSkillReadable(skillId, userId);
        return skillService.getSkillById(skillId, userId);
    }

    private static String normalizeDomain(String domain) {
        return domain == null ? "" : domain.trim().toLowerCase();
    }

    /**
     * 文本清洗 + 上限校验：MioBot 工具路径不经 Bean Validation，@Size 不会生效。
     * name 用严格模式（不许标签），描述与人设走富文本白名单。
     */
    private static String cleanIfPresent(String raw, String field, int maxLength) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String cleaned = "name".equals(field) || "icon".equals(field)
                ? XssUtils.cleanStrict(trimmed) : XssUtils.cleanRichText(trimmed);
        if (cleaned == null || cleaned.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, field + " 清理后为空，请去除 HTML 标签");
        }
        if (cleaned.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    field + " 超过 " + maxLength + " 字符上限（当前 " + cleaned.length() + "）");
        }
        return cleaned;
    }

    /**
     * 由结构化参数构造检索配置：不接收模型拼的 JSON——fromJson 对非法输入静默回退默认值，
     * 错配置会悄悄生效。越界直接报错，让模型看到可自纠的原因。
     */
    public static RetrievalConfig retrievalConfig(RetrievalOptions options) {
        RetrievalConfig config = RetrievalConfig.defaults();
        if (options == null) {
            return config;
        }
        if (options.topK() != null) {
            if (options.topK() < 1 || options.topK() > 50) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "topK 需在 1~50 之间");
            }
            config.setTopK(options.topK());
        }
        if (options.threshold() != null) {
            if (options.threshold() < 0 || options.threshold() > 1) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "threshold 需在 0~1 之间");
            }
            config.setThreshold(options.threshold());
        }
        if (options.method() != null && !options.method().isBlank()) {
            String method = options.method().trim();
            if (!RetrievalConfig.METHOD_HYBRID.equalsIgnoreCase(method)
                    && !RetrievalConfig.METHOD_VECTOR.equalsIgnoreCase(method)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "method 只能是 hybrid 或 vector");
            }
            config.setMethod(RetrievalConfig.METHOD_HYBRID.equalsIgnoreCase(method)
                    ? RetrievalConfig.METHOD_HYBRID : RetrievalConfig.METHOD_VECTOR);
        }
        return config;
    }

    private static String describe(RetrievalConfig config) {
        return "topK " + config.getTopK() + "，阈值 " + config.getThreshold()
                + "，方式 " + config.getMethod();
    }

    private static String statusText(Integer status) {
        AgentStatusEnum value = status == null ? null : AgentStatusEnum.getByCode(status);
        return value == null ? "未知状态" : value.getDesc();
    }

    private static String truncate(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= LIST_DESC_LENGTH
                ? single : single.substring(0, LIST_DESC_LENGTH) + "…";
    }
}
