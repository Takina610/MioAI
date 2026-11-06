package com.mio.ai.bot.resource;

import com.mio.ai.bot.service.resource.ResourceAdminService;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.model.dto.knowledge.KnowledgeBaseAddRequest;
import com.mio.ai.resource.model.dto.mcp.McpToolAddRequest;
import com.mio.ai.resource.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.resource.service.agent.AgentGroupService;
import com.mio.ai.resource.service.agent.AgentKnowledgeService;
import com.mio.ai.resource.service.agent.AgentMcpService;
import com.mio.ai.resource.service.agent.AgentService;
import com.mio.ai.resource.service.agent.AgentSkillService;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.mcp.McpToolService;
import com.mio.ai.resource.service.mcp.McpValidateService;
import com.mio.ai.resource.service.security.AccessGuardService;
import com.mio.ai.resource.service.skill.SkillService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

/**
 * MCP / 知识库创建的边界测试。
 * <p>两条核心：MCP 必须先在线校验通过才落库（否则存进去的是必然运行失败的配置）；
 * 知识库分块参数越界要报错而不是静默 clamp。
 */
class McpAndKbCreateTest {

    private static final Long USER_ID = 1L;

    private McpValidateService mcpValidate;
    private McpToolService mcpToolService;
    private KnowledgeBaseService kbService;
    private ResourceAdminService service;

    @BeforeEach
    void setUp() {
        mcpValidate = Mockito.mock(McpValidateService.class);
        mcpToolService = Mockito.mock(McpToolService.class);
        kbService = Mockito.mock(KnowledgeBaseService.class);
        service = new ResourceAdminService(
                Mockito.mock(AccessGuardService.class),
                Mockito.mock(AgentService.class),
                Mockito.mock(AgentGroupService.class),
                Mockito.mock(AgentKnowledgeService.class),
                Mockito.mock(AgentMcpService.class),
                Mockito.mock(AgentSkillService.class),
                kbService,
                mcpToolService,
                Mockito.mock(SkillService.class),
                mcpValidate);
    }

    private McpValidateResultVO result(boolean success, String error, String type) {
        McpValidateResultVO vo = new McpValidateResultVO();
        vo.setSuccess(success);
        vo.setErrorMessage(error);
        vo.setErrorType(type);
        vo.setTools(List.of());
        return vo;
    }

    // ---------- MCP ----------

    @Test
    void mcpIsSavedOnlyAfterValidationSucceeds() {
        Mockito.when(mcpValidate.validateMcpConfig(Mockito.any(), Mockito.eq(USER_ID)))
                .thenReturn(result(true, null, null));
        Mockito.when(mcpToolService.addMcpTool(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(88L);

        String reply = service.createMcp(USER_ID, "deepwiki",
                "维基百科 MCP", "{\"mcpServers\":{\"deepwiki\":{\"url\":\"https://mcp.deepwiki.com/mcp\"}}}");

        Assertions.assertTrue(reply.contains("#88"), reply);
        ArgumentCaptor<McpToolAddRequest> captor = ArgumentCaptor.forClass(McpToolAddRequest.class);
        Mockito.verify(mcpToolService).addMcpTool(captor.capture(), Mockito.eq(USER_ID));
        Assertions.assertEquals("deepwiki", captor.getValue().getName());
        // toolInfo NOT NULL：必须显式写入（哪怕是空数组）
        Assertions.assertNotNull(captor.getValue().getToolInfo());
    }

    @Test
    void mcpIsNotSavedWhenValidationFails() {
        Mockito.when(mcpValidate.validateMcpConfig(Mockito.any(), Mockito.eq(USER_ID)))
                .thenReturn(result(false, "连接失败：Connection refused", "CONNECTION_FAILED"));

        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> service.createMcp(USER_ID, "bad",
                        null, "{\"mcpServers\":{\"x\":{\"url\":\"https://nope.example/mcp\"}}}"));

        Assertions.assertTrue(e.getMessage().contains("CONNECTION_FAILED"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("Connection refused"), e.getMessage());
        // 关键：校验失败绝不能落库，否则用户拿到一个"看起来建好了但用不了"的工具
        Mockito.verify(mcpToolService, Mockito.never()).addMcpTool(Mockito.any(), Mockito.any());
    }

    @Test
    void mcpRequiresNameAndConfig() {
        Assertions.assertThrows(BusinessException.class,
                () -> service.createMcp(USER_ID, "  ", null, "{}"));
        Assertions.assertThrows(BusinessException.class,
                () -> service.createMcp(USER_ID, "名字", null, "  "));
        Mockito.verifyNoInteractions(mcpToolService);
    }

    // ---------- 知识库 ----------

    @Test
    void knowledgeBaseIsCreatedWithGivenChunkConfig() {
        Mockito.when(kbService.addKnowledgeBase(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(15L);

        service.createKnowledgeBase(USER_ID, "产品文档", "产品手册", 400, 80);

        ArgumentCaptor<KnowledgeBaseAddRequest> captor =
                ArgumentCaptor.forClass(KnowledgeBaseAddRequest.class);
        Mockito.verify(kbService).addKnowledgeBase(captor.capture(), Mockito.eq(USER_ID));
        Assertions.assertEquals("产品文档", captor.getValue().getName());
        Assertions.assertEquals(400, captor.getValue().getChunkSize());
        Assertions.assertEquals(80, captor.getValue().getChunkOverlap());
    }

    @Test
    void chunkSizeOutOfRangeIsRejectedNotClamped() {
        // 静默 clamp 会让用户以为设置生效了，这里必须报错
        Assertions.assertThrows(BusinessException.class,
                () -> service.createKnowledgeBase(USER_ID, "库", null, 100, null));
        Assertions.assertThrows(BusinessException.class,
                () -> service.createKnowledgeBase(USER_ID, "库", null, 5000, null));
        Mockito.verify(kbService, Mockito.never()).addKnowledgeBase(Mockito.any(), Mockito.any());
    }

    @Test
    void chunkOverlapIsCappedByHalfOfChunkSize() {
        // chunkSize=400 → 上限 min(500, 200) = 200
        Assertions.assertThrows(BusinessException.class,
                () -> service.createKnowledgeBase(USER_ID, "库", null, 400, 201));
        Mockito.when(kbService.addKnowledgeBase(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(1L);
        service.createKnowledgeBase(USER_ID, "库", null, 400, 200);
        Mockito.verify(kbService).addKnowledgeBase(Mockito.any(), Mockito.eq(USER_ID));
    }

    @Test
    void omittedChunkConfigOffersDefaultsInReply() {
        Mockito.when(kbService.addKnowledgeBase(Mockito.any(), Mockito.eq(USER_ID))).thenReturn(7L);
        String reply = service.createKnowledgeBase(USER_ID, "库", null, null, null);
        Assertions.assertTrue(reply.contains("800"), "回执要告诉用户实际生效的分块大小: " + reply);
        Assertions.assertTrue(reply.contains("100"), "回执要告诉用户实际生效的重叠: " + reply);
        Assertions.assertTrue(reply.contains("#7"), reply);
    }

    @Test
    void anonymousCannotCreateMcpOrKb() {
        Assertions.assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(),
                Assertions.assertThrows(BusinessException.class,
                        () -> service.createMcp(null, "n", null, "{}")).getCode());
        Assertions.assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(),
                Assertions.assertThrows(BusinessException.class,
                        () -> service.createKnowledgeBase(null, "n", null, null, null)).getCode());
    }
}
