package com.mio.ai.bot.agent.ui;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * resource_card 块校验：MCP / 知识库预览卡片的跳转与 id 真实性。
 * <p>与 agent_card 同一条安全线：路径后端生成、id 必须真实数字，杜绝模型编造。
 */
class ResourceCardBlockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> validate(String json) throws Exception {
        return UiBlockValidator.validateBlock(MAPPER.readTree(json), url -> null);
    }

    @Test
    void mcpCardJumpsToConfigPage() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"resource_card","id":"88","name":"deepwiki","resourceType":"mcp",
                 "detail":"12 个工具","status":"已启用"}""");

        Assertions.assertEquals("88", block.get("resourceId"));
        Assertions.assertEquals("mcp", block.get("resourceType"));
        Assertions.assertEquals("12 个工具", block.get("detail"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        Assertions.assertEquals(2, actions.size());
        Assertions.assertEquals("/dashboard/mcp/88", actions.get(0).get("path"));
    }

    @Test
    void knowledgeCardOpensUploadStepDirectly() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"resource_card","id":"15","name":"产品文档","resourceType":"knowledge"}""");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        // ?upload=1 让详情页挂载即打开上传弹框，省掉用户再点一次
        Assertions.assertEquals("/dashboard/knowledge/15?upload=1", actions.get(0).get("path"));
        Assertions.assertEquals("上传文档", actions.get(0).get("label"));
    }

    @Test
    void inventedIdIsRejected() {
        for (String fake : List.of("mcp-01", "kb_new", "deepwiki")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> validate("""
                    {"kind":"resource_card","id":"%s","name":"x","resourceType":"mcp"}"""
                    .formatted(fake)), "假 id 必须被拒：" + fake);
        }
    }

    @Test
    void unknownResourceTypeIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> validate("""
                {"kind":"resource_card","id":"1","name":"x","resourceType":"agent"}"""));
    }

    @Test
    void nameAndIdAreRequired() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validate("{\"kind\":\"resource_card\",\"id\":\"1\",\"resourceType\":\"mcp\"}"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validate("{\"kind\":\"resource_card\",\"name\":\"x\",\"resourceType\":\"mcp\"}"));
    }
}
