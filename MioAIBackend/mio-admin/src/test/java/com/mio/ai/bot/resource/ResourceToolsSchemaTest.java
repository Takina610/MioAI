package com.mio.ai.bot.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.bot.agent.resource.ResourceTools;
import com.mio.ai.bot.service.resource.ResourceAdminService;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.tools.contract.ToolContext;
import com.mio.ai.framework.zagent.tools.contract.ToolEntry;
import com.mio.ai.framework.zagent.tools.contract.ToolRegistry;
import com.mio.ai.framework.zagent.tools.contract.ToolUseFailure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资源管理工具的 schema 与参数边界测试。
 * <p>注册期只校验 schema 能否被 JSON 解析——解析失败会被静默丢弃，模型只能瞎猜参数
 * （{@code ToolRegistry.register} 与 {@code UiRenderTool} 注释都记过这个坑），所以这里逐条解析。
 * 同时验证工具层把业务失败转成可自纠的 tool_use_error，而不是抛异常打断轮次。
 */
class ResourceToolsSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ToolRegistry registry(ResourceAdminService service, Long userId) {
        ToolRegistry registry = new ToolRegistry(new ToolContext(
                "chat-1", new ConversationState(), null, null, null, null));
        // ingestService 传 null：未接知识库摄取时不注册 kb_upload（沙箱关闭的场景）
        ResourceTools.registerAll(registry, service, null, userId, "chat-1", List.of());
        return registry;
    }

    private static ToolRegistry registryWith(Long userId) {
        return registry(Mockito.mock(ResourceAdminService.class), userId);
    }

    private static Set<String> names(ToolRegistry registry) {
        return registry.entries().stream().map(entry -> entry.name).collect(Collectors.toSet());
    }

    private static ToolEntry entryNamed(ToolRegistry registry, String name) {
        return registry.entries().stream()
                .filter(entry -> name.equals(entry.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未注册工具 " + name));
    }

    @Test
    void registersAllTools() {
        // 无 ingestService 时 kb_upload 不注册（沙箱关闭/无附件通道的场景）
        Assertions.assertEquals(
                Set.of("resource_list", "agent_save", "group_create", "agent_bind",
                        "mcp_create", "kb_create"),
                names(registryWith(7L)));
    }

    @Test
    void anonymousSessionRegistersNothing() {
        // userId 为 null（游客/子代理）时不注册：写操作都需要登录态
        Assertions.assertTrue(names(registryWith(null)).isEmpty());
    }

    @Test
    void everySchemaIsValidJson() {
        ToolRegistry registry = registryWith(7L);
        for (String tool : List.of("resource_list", "agent_save", "group_create", "agent_bind",
                "mcp_create", "kb_create")) {
            ToolEntry entry = entryNamed(registry, tool);
            Assertions.assertNotNull(entry.inputSchema, tool + " 缺少 inputSchema");
            try {
                JsonNode schema = MAPPER.readTree(entry.inputSchema);
                Assertions.assertTrue(schema.path("properties").isObject(),
                        tool + " 的 schema 应有 properties 对象");
            } catch (Exception e) {
                Assertions.fail(tool + " 的 schema 不是合法 JSON：" + e.getMessage());
            }
        }
    }

    @Test
    void schemasDeclareTheirRequiredArguments() throws Exception {
        ToolRegistry registry = registryWith(7L);
        Assertions.assertEquals(List.of("domain"),
                requiredOf(MAPPER.readTree(entryNamed(registry, "resource_list").inputSchema)));
        Assertions.assertTrue(requiredOf(
                        MAPPER.readTree(entryNamed(registry, "agent_save").inputSchema)).isEmpty(),
                "agent_save 创建/更新两用，不能强制任何字段");
        // group_create 只强制 name：成员既可给名字也可给 id，二者都不强制
        Assertions.assertEquals(List.of("name"),
                requiredOf(MAPPER.readTree(entryNamed(registry, "group_create").inputSchema)));
        // agent_bind 只强制 action+domain：目标与资源既可用名字也可用 id
        Assertions.assertEquals(List.of("action", "domain"),
                requiredOf(MAPPER.readTree(entryNamed(registry, "agent_bind").inputSchema)));
    }

    @Test
    void resourceListIsReadOnlyAndBindingsAreSerialized() {
        // 只读工具进并行分组；写工具必须串行执行，避免并发写同一智能体的绑定
        ToolRegistry registry = registryWith(7L);
        Assertions.assertTrue(entryNamed(registry, "resource_list").parallelizable());
        for (String tool : List.of("agent_save", "group_create", "agent_bind")) {
            Assertions.assertFalse(entryNamed(registry, tool).parallelizable(),
                    tool + " 有副作用，不能并行执行");
        }
    }

    @Test
    void businessFailureBecomesToolUseError() throws Exception {
        // 服务层拒绝（未知 domain）必须转成 tool_use_error 信封：模型下一轮能看到原因并自纠
        ResourceAdminService service = Mockito.mock(ResourceAdminService.class);
        Mockito.when(service.list(Mockito.anyLong(), Mockito.anyString(), Mockito.any()))
                .thenThrow(new BusinessException(ErrorCode.PARAMS_ERROR, "未知的资源类型：bogus"));
        ToolEntry list = entryNamed(registry(service, 7L), "resource_list");

        ToolUseFailure failure = Assertions.assertThrows(ToolUseFailure.class,
                () -> list.handler().execute(MAPPER.readTree("{\"domain\":\"bogus\"}"), null));
        Assertions.assertTrue(failure.getMessage().contains("未知的资源类型"), failure.getMessage());
        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), failure.errorCode());
        Assertions.assertTrue(failure.toModelContent().startsWith("<tool_use_error>"));
    }

    @Test
    void invalidActionIsRejectedBeforeTouchingServices() throws Exception {
        ToolEntry bind = entryNamed(registryWith(7L), "agent_bind");
        ToolUseFailure failure = Assertions.assertThrows(ToolUseFailure.class,
                () -> bind.handler().execute(MAPPER.readTree(
                        "{\"action\":\"create\",\"agentId\":1,\"domain\":\"mcp\",\"resourceId\":2}"), null));
        Assertions.assertTrue(failure.getMessage().contains("bind"), failure.getMessage());
    }

    @Test
    void memberIdListAcceptsBothArrayAndCommaString() throws Exception {
        // 弱模型常把数组传成 "1,2"：宽容归一，不让整轮失败
        ResourceAdminService service = Mockito.mock(ResourceAdminService.class);
        Mockito.when(service.createGroup(Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyList(), Mockito.any())).thenReturn("ok");
        ToolEntry group = entryNamed(registry(service, 7L), "group_create");

        group.handler().execute(MAPPER.readTree("{\"name\":\"组\",\"memberAgentIds\":\"11, 12\"}"), null);
        group.handler().execute(MAPPER.readTree("{\"name\":\"组\",\"memberAgentIds\":[13,14]}"), null);

        Mockito.verify(service).createGroup(7L, "组", List.of(11L, 12L), null);
        Mockito.verify(service).createGroup(7L, "组", List.of(13L, 14L), null);
    }

    private static List<String> requiredOf(JsonNode schema) {
        JsonNode required = schema.path("required");
        if (required == null || !required.isArray()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        required.forEach(node -> fields.add(node.asText()));
        return fields;
    }
}
