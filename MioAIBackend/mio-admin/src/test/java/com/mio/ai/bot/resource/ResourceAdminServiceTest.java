package com.mio.ai.bot.resource;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mio.ai.bot.service.resource.ResourceAdminService;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.framework.rag.retrieval.RetrievalConfig;
import com.mio.ai.resource.model.dto.agent.AgentAddRequest;
import com.mio.ai.resource.model.dto.agent.AgentKnowledgeAddRequest;
import com.mio.ai.resource.model.entity.agent.Agent;
import com.mio.ai.resource.model.entity.agent.AgentSkill;
import com.mio.ai.resource.model.enums.agent.AgentTypeEnum;
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

/**
 * 资源管理服务的业务边界测试：鉴权、类型限制、绑定去重、检索配置规范化。
 * <p>这几条都是"服务层不校验、controller 才校验"的地方——工具路径绕过了 controller，
 * 所以这里必须逐条钉住，否则对话里能越权改别人的智能体或写进错的检索配置。
 */
class ResourceAdminServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long AGENT_ID = 12L;

    private AccessGuardService accessGuard;
    private AgentService agentService;
    private AgentGroupService groupService;
    private AgentKnowledgeService knowledgeBinding;
    private AgentMcpService mcpBinding;
    private AgentSkillService skillBinding;
    private KnowledgeBaseService kbService;
    private McpToolService mcpService;
    private SkillService skillService;
    private ResourceAdminService service;

    @BeforeEach
    void setUp() {
        accessGuard = Mockito.mock(AccessGuardService.class);
        agentService = Mockito.mock(AgentService.class);
        groupService = Mockito.mock(AgentGroupService.class);
        knowledgeBinding = Mockito.mock(AgentKnowledgeService.class);
        mcpBinding = Mockito.mock(AgentMcpService.class);
        skillBinding = Mockito.mock(AgentSkillService.class);
        kbService = Mockito.mock(KnowledgeBaseService.class);
        mcpService = Mockito.mock(McpToolService.class);
        skillService = Mockito.mock(SkillService.class);
        service = new ResourceAdminService(accessGuard, agentService, groupService,
                knowledgeBinding, mcpBinding, skillBinding, kbService, mcpService, skillService);
        Mockito.when(knowledgeBinding.getKbIdsByAgentId(AGENT_ID)).thenReturn(List.of());
        Mockito.when(mcpBinding.getMcpIdsByAgentId(AGENT_ID)).thenReturn(List.of());
    }

    private Agent agentOfType(AgentTypeEnum type) {
        Agent agent = new Agent();
        agent.setId(AGENT_ID);
        agent.setUserId(USER_ID);
        agent.setType(type.getCode());
        return agent;
    }

    private KnowledgeBaseVO kb(long id, String name) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    // ---------- 登录态与类型限制 ----------

    @Test
    void anonymousCallIsRejectedByEveryEntryPoint() {
        for (Runnable call : List.<Runnable>of(
                () -> service.list(null, "agent", null),
                () -> service.saveAgent(null, null, "名", null, null, null, null),
                () -> service.createGroup(null, "群", List.of(1L, 2L), null),
                () -> service.bind(null, true, AGENT_ID, "knowledge", 1L, null, null))) {
            BusinessException e = Assertions.assertThrows(BusinessException.class, call::run);
            Assertions.assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), e.getCode());
        }
    }

    @Test
    void cannotBindToGroupOrBuiltinAgent() {
        // 群聊（2）不参与装配编辑；checkAgentOwner 先通过，类型校验再拒
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.GROUP));

        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.bind(USER_ID, true, AGENT_ID, "knowledge", 1L, null, null));
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        Assertions.assertTrue(e.getMessage().contains("自定义智能体"), e.getMessage());
        Mockito.verifyNoInteractions(knowledgeBinding);
    }

    @Test
    void updateRejectsAgentOwnedByOthersViaGuard() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该智能体"));

        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.saveAgent(USER_ID, AGENT_ID, "改名", null, null, null, null));
        Assertions.assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), e.getCode());
        Mockito.verify(agentService, Mockito.never()).updateAgent(Mockito.any(), Mockito.any());
    }

    // ---------- 智能体创建 ----------

    @Test
    void createAgentCleansMarkupAndRequiresName() {
        Mockito.when(agentService.addAgent(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(99L);

        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.saveAgent(USER_ID, null, "  ", null, null, null, null));
        Assertions.assertTrue(e.getMessage().contains("name"), e.getMessage());

        service.saveAgent(USER_ID, null, "<script>alert(1)</script>产品助手", null, "你是助手", null, null);
        ArgumentCaptor<AgentAddRequest> captor = ArgumentCaptor.forClass(AgentAddRequest.class);
        Mockito.verify(agentService).addAgent(captor.capture(), Mockito.eq(USER_ID));
        // cleanStrict 与既有 @XssClean(strict) 同源（Safelist.basic）：危险标签剥离，基础标签保留
        Assertions.assertEquals("产品助手", captor.getValue().getName());
        Assertions.assertEquals("你是助手", captor.getValue().getSystemPrompt());
    }

    @Test
    void createAgentRejectsOverlongName() {
        String tooLong = "名".repeat(101);
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.saveAgent(USER_ID, null, tooLong, null, null, null, null));
        Assertions.assertTrue(e.getMessage().contains("100"), e.getMessage());
        Mockito.verify(agentService, Mockito.never()).addAgent(Mockito.any(), Mockito.any());
    }

    // ---------- 图标：结构化 shape+color ----------

    @Test
    void shapeAndColorBecomeCanonicalIconJson() {
        Mockito.when(agentService.addAgent(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(99L);

        service.saveAgent(USER_ID, null, "翻译官", null, "你是翻译", "hex", "cyan");

        ArgumentCaptor<AgentAddRequest> captor = ArgumentCaptor.forClass(AgentAddRequest.class);
        Mockito.verify(agentService).addAgent(captor.capture(), Mockito.eq(USER_ID));
        Assertions.assertEquals(
                "{\"shape\":\"hex\",\"fill\":{\"kind\":\"palette\",\"id\":\"cyan\"}}",
                captor.getValue().getIcon(),
                "前端 parseBotIcon 只认规范 JSON，格式错会静默回退默认图标");
    }

    @Test
    void omittedIconLeavesIconNull() {
        Mockito.when(agentService.addAgent(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(99L);

        service.saveAgent(USER_ID, null, "助手", null, "你是助手", null, null);

        ArgumentCaptor<AgentAddRequest> captor = ArgumentCaptor.forClass(AgentAddRequest.class);
        Mockito.verify(agentService).addAgent(captor.capture(), Mockito.eq(USER_ID));
        Assertions.assertNull(captor.getValue().getIcon(), "没指定图标时不能凭空写入默认值");
    }

    @Test
    void invalidShapeOrColorIsRejectedNotSilentlyCoerced() {
        // 静默纠正会让用户以为设置生效了；这里必须报出可选集合
        BusinessException shapeErr = Assertions.assertThrows(BusinessException.class,
                () -> service.saveAgent(USER_ID, null, "助手", null, null, "triangle", "cyan"));
        Assertions.assertTrue(shapeErr.getMessage().contains("blob"), shapeErr.getMessage());

        BusinessException colorErr = Assertions.assertThrows(BusinessException.class,
                () -> service.saveAgent(USER_ID, null, "助手", null, null, "blob", "rainbow"));
        Assertions.assertTrue(colorErr.getMessage().contains("cyan"), colorErr.getMessage());

        Mockito.verify(agentService, Mockito.never()).addAgent(Mockito.any(), Mockito.any());
    }

    // ---------- 名字 → id 解析 ----------

    @Test
    void resolvesAgentByExactName() {
        mockCustomAgents(agent(57L, "美食推荐官"), agent(50L, "翻译专家"));

        Assertions.assertEquals(57L, service.resolveAgentIdByName(USER_ID, "美食推荐官"));
    }

    @Test
    void exactMatchWinsOverSubstringMatch() {
        // LIKE 查询会同时命中"助手"和"助手A"，完全同名必须优先，否则会绑错对象
        mockCustomAgents(agent(1L, "我的助手A"), agent(2L, "助手"));

        Assertions.assertEquals(2L, service.resolveAgentIdByName(USER_ID, "助手"));
    }

    @Test
    void uniqueSubstringMatchIsAccepted() {
        mockCustomAgents(agent(9L, "专业翻译专家"));

        Assertions.assertEquals(9L, service.resolveAgentIdByName(USER_ID, "翻译"));
    }

    @Test
    void ambiguousMatchReportsCandidatesInsteadOfGuessing() {
        mockCustomAgents(agent(1L, "翻译专家甲"), agent(2L, "翻译专家乙"));

        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.resolveAgentIdByName(USER_ID, "翻译专家"));
        Assertions.assertTrue(e.getMessage().contains("#1"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("#2"), e.getMessage());
    }

    @Test
    void unknownNameReturnsNull() {
        mockCustomAgents();
        Assertions.assertNull(service.resolveAgentIdByName(USER_ID, "不存在"));
    }

    @Test
    void resourceNameLookupRejectsUnknownDomain() {
        // agent 不是绑定目标域：必须明确报错，而不是悄悄当成知识库查
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.resolveResourceIdByName(USER_ID, "agent", "x"));
        Assertions.assertTrue(e.getMessage().contains("未知"), e.getMessage());
    }

    private static com.mio.ai.resource.model.vo.agent.AgentVO agent(long id, String name) {
        com.mio.ai.resource.model.vo.agent.AgentVO vo = new com.mio.ai.resource.model.vo.agent.AgentVO();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    private void mockCustomAgents(com.mio.ai.resource.model.vo.agent.AgentVO... agents) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.mio.ai.resource.model.vo.agent.AgentVO> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 50);
        page.setRecords(List.of(agents));
        Mockito.when(agentService.queryAgents(Mockito.any())).thenReturn(page);
    }

    // ---------- 知识库绑定 ----------

    @Test
    void bindingKnowledgeWritesCanonicalConfig() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.CUSTOM));
        Mockito.when(kbService.getKnowledgeBaseById(5L)).thenReturn(kb(5L, "产品文档"));
        Mockito.when(knowledgeBinding.addAgentKnowledge(Mockito.any())).thenReturn(31L);

        service.bind(USER_ID, true, AGENT_ID, "knowledge", 5L,
                new ResourceAdminService.RetrievalOptions(8, 0.8, "vector"), null);

        ArgumentCaptor<AgentKnowledgeAddRequest> captor =
                ArgumentCaptor.forClass(AgentKnowledgeAddRequest.class);
        Mockito.verify(knowledgeBinding).addAgentKnowledge(captor.capture());
        AgentKnowledgeAddRequest saved = captor.getValue();
        Assertions.assertEquals(AGENT_ID, saved.getAgentId());
        Assertions.assertEquals(5L, saved.getKbId());
        Assertions.assertEquals(1, saved.getEnabled());

        // 落库的必须是规范 camelCase 且值已生效（fromJson 对非法输入静默回退，所以只能靠这里保证）
        RetrievalConfig parsed = RetrievalConfig.fromJson(saved.getRetrievalConfig());
        Assertions.assertEquals(8, parsed.getTopK());
        Assertions.assertEquals(0.8, parsed.getThreshold(), 1e-9);
        Assertions.assertEquals(RetrievalConfig.METHOD_VECTOR, parsed.getMethod());
        JSONObject raw = JSONUtil.parseObj(saved.getRetrievalConfig());
        Assertions.assertEquals(8, raw.getInt("topK"));
        Assertions.assertTrue(raw.containsKey("enableRerank"), "未传的字段也要带默认值落库");
    }

    @Test
    void bindingKnowledgeIsIdempotent() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.CUSTOM));
        Mockito.when(kbService.getKnowledgeBaseById(5L)).thenReturn(kb(5L, "产品文档"));
        Mockito.when(knowledgeBinding.getKbIdsByAgentId(AGENT_ID)).thenReturn(List.of(5L));

        String reply = service.bind(USER_ID, true, AGENT_ID, "knowledge", 5L, null, null);

        Assertions.assertTrue(reply.contains("已绑定"), reply);
        Mockito.verify(knowledgeBinding, Mockito.never()).addAgentKnowledge(Mockito.any());
    }

    @Test
    void unbindReportsWhetherAnythingWasRemoved() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.CUSTOM));
        Mockito.when(kbService.getKnowledgeBaseById(5L)).thenReturn(kb(5L, "产品文档"));
        Mockito.when(knowledgeBinding.deleteByAgentIdAndKbId(AGENT_ID, 5L)).thenReturn(true, false);

        Assertions.assertTrue(service.bind(USER_ID, false, AGENT_ID, "knowledge", 5L, null, null)
                .contains("已解绑"));
        Assertions.assertTrue(service.bind(USER_ID, false, AGENT_ID, "knowledge", 5L, null, null)
                .contains("本来就没有绑定"));
    }

    @Test
    void retrievalOptionsAreValidatedNotClamped() {
        // 越界直接报错：静默 clamp 会让用户以为设置生效了
        for (Runnable bad : List.<Runnable>of(
                () -> ResourceAdminService.retrievalConfig(
                        new ResourceAdminService.RetrievalOptions(0, null, null)),
                () -> ResourceAdminService.retrievalConfig(
                        new ResourceAdminService.RetrievalOptions(51, null, null)),
                () -> ResourceAdminService.retrievalConfig(
                        new ResourceAdminService.RetrievalOptions(null, 1.5, null)),
                () -> ResourceAdminService.retrievalConfig(
                        new ResourceAdminService.RetrievalOptions(null, -0.1, null)),
                () -> ResourceAdminService.retrievalConfig(
                        new ResourceAdminService.RetrievalOptions(null, null, "bm25")))) {
            Assertions.assertThrows(BusinessException.class, bad::run);
        }
    }

    @Test
    void omittedRetrievalOptionsKeepDefaults() {
        RetrievalConfig config = ResourceAdminService.retrievalConfig(null);
        Assertions.assertEquals(RetrievalConfig.DEFAULT_TOP_K, config.getTopK());
        Assertions.assertEquals(RetrievalConfig.DEFAULT_THRESHOLD, config.getThreshold(), 1e-9);
        Assertions.assertEquals(RetrievalConfig.DEFAULT_METHOD, config.getMethod());
    }

    // ---------- MCP / 技能绑定 ----------

    @Test
    void bindingMcpIsIdempotentAndNeverSendsConfigOverride() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.CUSTOM));
        McpToolVO tool = new McpToolVO();
        tool.setId(3L);
        tool.setName("github");
        Mockito.when(mcpService.getMcpToolById(3L, USER_ID, false)).thenReturn(tool);
        Mockito.when(mcpBinding.getMcpIdsByAgentId(AGENT_ID)).thenReturn(List.of(3L));

        Assertions.assertTrue(service.bind(USER_ID, true, AGENT_ID, "mcp", 3L, null, null)
                .contains("已绑定"));
        Mockito.verify(mcpBinding, Mockito.never())
                .addAgentMcp(Mockito.any(), Mockito.any());
    }

    @Test
    void bindingSkillPassesEnabledFlag() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.CUSTOM));
        SkillVO skill = new SkillVO();
        skill.setId(8L);
        skill.setName("搜索");
        Mockito.when(skillService.getSkillById(8L, USER_ID)).thenReturn(skill);
        Mockito.when(skillBinding.addAgentSkill(Mockito.any())).thenReturn(44L);

        service.bind(USER_ID, true, AGENT_ID, "skill", 8L, null, 0);

        ArgumentCaptor<AgentSkill> captor = ArgumentCaptor.forClass(AgentSkill.class);
        Mockito.verify(skillBinding).addAgentSkill(captor.capture());
        Assertions.assertEquals(AGENT_ID, captor.getValue().getAgentId());
        Assertions.assertEquals(8L, captor.getValue().getSkillId());
        Assertions.assertEquals(0, captor.getValue().getEnabled(), "enabled=0 要原样透传（保持禁用）");
    }

    @Test
    void bindRejectsUnsupportedDomain() {
        Mockito.when(accessGuard.checkAgentOwner(AGENT_ID, USER_ID))
                .thenReturn(agentOfType(AgentTypeEnum.CUSTOM));

        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.bind(USER_ID, true, AGENT_ID, "agent", 1L, null, null));
        Assertions.assertTrue(e.getMessage().contains("knowledge"), e.getMessage());
    }
}
