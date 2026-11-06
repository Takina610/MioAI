package com.mio.ai.bot.agent.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.bot.service.resource.AgentIconSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * agent_card 群的头像规则回归测试。
 * <p>产品规则：群聊头像 = 成员图标按 2/3/4/5+ 自动堆叠，**不能**是单一 BotIcon，
 * 也不需要用户选。这里钉住"群聊块绝不带 icon、只带 members"，以及成员图标逐条规范化。
 */
class GroupCardAvatarTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Map<String, Object> validate(String json) throws Exception {
        return UiBlockValidator.validateBlock(MAPPER.readTree(json), url -> null);
    }

    /** 2 成员群：应带 2 条 members，且没有 icon（否则前端 v-if 会顶掉 GroupAvatar） */
    @Test
    void twoMemberGroupCarriesMembersOnly() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"58","name":"双人组","target":"group",
                 "members":[
                   {"id":"57","name":"美食推荐官","shape":"teardrop","color":"red"},
                   {"id":"50","name":"翻译专家","shape":"hex","color":"cyan"}
                 ]}""");

        Assertions.assertFalse(block.containsKey("icon"),
                "群聊带 icon 会让前端渲染单一图标，顶掉成员堆叠头像");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) block.get("members");
        Assertions.assertEquals(2, members.size());
    }

    /** 成员图标缺失/非法时不丢成员（GroupAvatar 回退首字），也不报错 */
    @Test
    void membersWithoutIconAreKept() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"58","name":"组","target":"group",
                 "members":[{"id":"1","name":"甲"},{"id":"2","name":"乙","shape":"unknown","color":"rainbow"}]}""");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) block.get("members");
        Assertions.assertEquals(2, members.size(), "图标非法不该丢成员");
        Assertions.assertFalse(members.get(1).containsKey("icon"));
    }

    /** 5+ 成员：全部保留（前端只展示前 4 + 省略号，由 GroupAvatar 自己裁） */
    @Test
    void fivePlusMembersAreAllPassedThrough() throws Exception {
        StringBuilder members = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            if (i > 1) {
                members.append(',');
            }
            members.append("{\"id\":\"").append(i).append("\",\"name\":\"成员").append(i)
                    .append("\",\"shape\":\"blob\",\"color\":\"cyan\"}");
        }
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"99","name":"大群","target":"group","members":[%s]}"""
                .formatted(members));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) block.get("members");
        Assertions.assertEquals(6, list.size());
        @SuppressWarnings("unchecked")
        Map<String, String> actions = ((List<Map<String, String>>) block.get("jumpActions")).get(0);
        Assertions.assertEquals("/chat/99", actions.get("path"));
    }

    /** 群聊只有"开始对话"一个按钮（群聊在对话内编辑，无 dashboard 页） */
    @Test
    void groupCardHasOnlyChatAction() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"58","name":"组","target":"group",
                 "members":[{"id":"1","name":"甲"}]}""");

        // target 必须原样保留 'group'：前端靠它决定渲染成员堆叠而非单一图标
        Assertions.assertEquals("group", block.get("target"),
                "target 被改写会让前端走单一图标分支，成员堆叠不出现");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        Assertions.assertEquals(1, actions.size());
        Assertions.assertEquals("开始对话", actions.get(0).get("label"));
    }

    /** 每个支持配色的成员都能生成规范 icon JSON（前端 parseBotIcon 才认） */
    @Test
    void everySuggestedColorProducesValidIcon() {
        for (String color : AgentIconSpec.suggestedColors()) {
            String icon = AgentIconSpec.toJson("blob", color);
            Assertions.assertTrue(icon.contains("\"id\":\"" + color + "\""), icon);
            Assertions.assertTrue(AgentIconSpec.isValidColor(color));
        }
    }

    /** 推荐配色必须是 4 个且互不相同（AskUserQuestion 每题上限 4 选项） */
    @Test
    void suggestedColorsFitOneQuestion() {
        List<String> colors = AgentIconSpec.suggestedColors();
        Assertions.assertEquals(4, colors.size(), "问答每题最多 4 个选项");
        Assertions.assertEquals(4, Set.copyOf(colors).size(), "配色不能重复");
    }

    /**
     * 幻觉防线：模型跳过 group_create 直接画卡片时，会编造 "group-01"/"translator" 这类假 id。
     * 这类块必须当场失败（实测中它导致卡片按钮跳到不存在页面）。
     */
    @Test
    void inventedNonNumericIdIsRejected() {
        for (String fakeId : List.of("group-01", "translator", "agent_new", "group_1")) {
            Assertions.assertThrows(IllegalArgumentException.class, () -> validate("""
                    {"kind":"agent_card","id":"%s","name":"双语数据组","target":"group"}"""
                    .formatted(fakeId)), "假 id 必须被拒：" + fakeId);
        }
    }

    @Test
    void realNumericIdIsAccepted() throws Exception {
        Map<String, Object> block = validate("""
                {"kind":"agent_card","id":"164","name":"双语数据组","target":"group",
                 "members":[{"id":"50","name":"翻译专家"}]}""");
        Assertions.assertEquals("164", block.get("agentId"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) block.get("jumpActions");
        Assertions.assertEquals("/chat/164", actions.get(0).get("path"));
    }
}
