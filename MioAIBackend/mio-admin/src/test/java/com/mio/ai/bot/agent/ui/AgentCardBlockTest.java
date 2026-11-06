package com.mio.ai.bot.agent.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * agent_card 块校验测试：预览卡片的跳转路径必须由后端生成，不接受模型自填 URL。
 * <p>这是安全边界——若放行模型给的 path，卡片就成了钓鱼按钮。
 * <p>放在 ui 包内，以便直接使用包私有的 {@link UiBlockValidator#validateBlock}。
 */
class AgentCardBlockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> validate(String json) throws Exception {
        return UiBlockValidator.validateBlock(MAPPER.readTree(json), url -> null);
    }

    @Test
    void agentCardCarriesBackendGeneratedJumpPaths() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"42","name":"翻译官","description":"中英互译",
                 "shape":"hex","color":"cyan","status":"草稿","target":"agent"}""");

        Assertions.assertEquals("agent_card", block.get("kind"));
        Assertions.assertEquals("42", block.get("agentId"), "用 agentId 而非 id，避免覆盖展示块主键");
        Assertions.assertEquals("翻译官", block.get("name"));
        Assertions.assertEquals("草稿", block.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        Assertions.assertEquals(2, actions.size(), "自定义智能体两个入口：对话 + 编辑");
        Assertions.assertEquals("/chat/42", actions.get(0).get("path"));
        Assertions.assertEquals("/dashboard/agent/42", actions.get(1).get("path"));
    }

    @Test
    void groupCardOnlyGetsChatEntry() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"58","name":"创意工坊","target":"group"}""");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        Assertions.assertEquals(1, actions.size(), "群聊在对话内编辑，不给 dashboard 入口");
        Assertions.assertEquals("/chat/58", actions.get(0).get("path"));
    }

    @Test
    void modelSuppliedPathIsIgnored() throws Exception {
        // 模型想自己指定跳转地址（钓鱼/外链）——必须被丢弃，只认后端拼的路径
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"42","name":"x",
                 "jumpActions":[{"label":"点我","path":"https://evil.example.com"}]}""");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        Assertions.assertTrue(actions.stream().allMatch(a -> a.get("path").startsWith("/")),
                "所有跳转路径必须是站内绝对路径：" + actions);
        Assertions.assertTrue(actions.stream().noneMatch(a -> a.get("path").contains("evil")));
    }

    @Test
    void iconIsNormalizedFromShapeAndColor() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"42","name":"x","shape":"gem","color":"violet"}""");

        Assertions.assertEquals(
                "{\"shape\":\"gem\",\"fill\":{\"kind\":\"palette\",\"id\":\"violet\"}}",
                block.get("icon"));
    }

    @Test
    void omittedIconProducesNoIconKey() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"42","name":"x"}""");
        Assertions.assertFalse(block.containsKey("icon"), "未指定时不写 icon，交给前端兜底");
    }

    @Test
    void groupCardUsesMemberAvatarsNotSingleIcon() throws Exception {
        // 群聊头像按规则由成员图标堆叠生成，必须带 members、且不能带单一 icon
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"58","name":"创意工坊","target":"group",
                 "shape":"blob","color":"black",
                 "members":[
                   {"id":"57","name":"美食推荐官","shape":"teardrop","color":"red"},
                   {"id":"50","name":"翻译专家","shape":"hex","color":"cyan"}
                 ]}""");

        Assertions.assertFalse(block.containsKey("icon"), "群聊不能渲染单一图标，否则会顶掉成员堆叠");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) block.get("members");
        Assertions.assertEquals(2, members.size());
        Assertions.assertEquals("57", members.get(0).get("id"));
        Assertions.assertEquals("美食推荐官", members.get(0).get("name"));
        Assertions.assertEquals(
                "{\"shape\":\"teardrop\",\"fill\":{\"kind\":\"palette\",\"id\":\"red\"}}",
                members.get(0).get("icon"),
                "成员图标逐条规范化，GroupAvatar 才能画出真实图标");
        Assertions.assertEquals("hex", iconShapeOf(members.get(1).get("icon")));
    }

    @Test
    void memberWithoutIconIsStillKept() throws Exception {
        // 老数据没图标：保留成员（前端回退首字方块），不要整条丢弃
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"58","name":"组","target":"group",
                 "members":[{"id":"1","name":"甲"},{"id":"2","name":"乙"}]}""");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) block.get("members");
        Assertions.assertEquals(2, members.size());
        Assertions.assertFalse(members.get(0).containsKey("icon"));
    }

    @Test
    void nonGroupCardIgnoresMembers() throws Exception {
        // target=agent 时 members 无意义，不应混进块里
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"42","name":"x","target":"agent","shape":"blob","color":"cyan",
                 "members":[{"id":"1","name":"甲"}]}""");

        Assertions.assertFalse(block.containsKey("members"));
        Assertions.assertNotNull(block.get("icon"));
    }

    private static String iconShapeOf(Object iconJson) {
        String shape = cn.hutool.json.JSONUtil.parseObj(String.valueOf(iconJson)).getStr("shape");
        return com.mio.ai.bot.service.resource.AgentIconSpec.isValidShape(shape) ? shape : null;
    }

    @Test
    void invalidShapeIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> validate("""
                {"kind":"agent_card","id":"42","name":"x","shape":"triangle","color":"cyan"}"""));
    }

    @Test
    void missingIdOrNameIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validate("{\"kind\":\"agent_card\",\"name\":\"x\"}"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> validate("{\"kind\":\"agent_card\",\"id\":\"42\"}"));
    }

    @Test
    void renderUiSchemaAdvertisesAgentCardAndItsFields() throws Exception {
        // 注册期 schema 解析失败会让模型瞎猜参数，这里钉住新增字段确实在 schema 里
        var field = UiRenderTool.class.getDeclaredField("SCHEMA");
        field.setAccessible(true);
        JsonNode tree = MAPPER.readTree((String) field.get(null));
        JsonNode itemProps = tree.path("properties").path("blocks").path("items").path("properties");

        String kindEnum = itemProps.path("kind").path("enum").toString();
        Assertions.assertTrue(kindEnum.contains("agent_card"), kindEnum);

        for (String name : List.of("id", "name", "description", "shape", "color", "status", "target")) {
            Assertions.assertTrue(itemProps.has(name), "schema 缺少 agent_card 字段 " + name);
        }
    }
}
