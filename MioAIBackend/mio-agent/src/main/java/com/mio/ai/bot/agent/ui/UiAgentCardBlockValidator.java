package com.mio.ai.bot.agent.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mio.ai.bot.service.resource.AgentIconSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * agent_card 块校验：智能体/群聊创建后的预览卡片（可点击跳转）。
 * <p>为什么需要独立 kind：cards 块只保留 title/value/desc/trend 且不可点击，
 * actions 块只能把 prompt 当聊天消息发出——两者都无法表达"看这个刚建好的东西，
 * 点进去继续操作"。这里由后端决定跳转路径，不接受模型给的任意 URL（防钓鱼）。
 */
final class UiAgentCardBlockValidator {

    private static final int MAX_ID_LENGTH = 32;
    private static final java.util.regex.Pattern DIGITS = java.util.regex.Pattern.compile("^\\d{1,18}$");

    private UiAgentCardBlockValidator() {
    }

    static Map<String, Object> validate(JsonNode raw) {
        Map<String, Object> block = new LinkedHashMap<>();
        String name = UiBlockValidator.text(raw, "name");
        if (name == null) {
            throw new IllegalArgumentException("agent_card needs a non-empty name");
        }
        block.put("name", name);

        String id = UiBlockValidator.text(raw, "id");
        if (id == null || id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("agent_card needs an id of at most " + MAX_ID_LENGTH + " chars");
        }
        // id 必须是真实存在的数字主键：模型编造 "group-01"/"translator" 这类假 id 时，
        // 卡片按钮会跳到不存在的页面（实测已发生）。只放行纯数字，幻觉当场失败。
        if (!DIGITS.matcher(id).matches()) {
            throw new IllegalArgumentException("agent_card id must be a real numeric agent id, got '" + id
                    + "' — call agent_save or group_create first and use the id from its result");
        }
        // 用 agentId 而非 id：块的 id 是前端展示块自身的主键，重名会覆盖它
        block.put("agentId", id);

        String description = UiBlockValidator.text(raw, "description");
        if (description != null) {
            block.put("description", description);
        }

        String status = UiBlockValidator.text(raw, "status");
        if (status != null) {
            block.put("status", status);
        }

        // target 表示"这是什么"（agent | group），前端据此决定渲染单一图标还是成员堆叠。
        // 跳转路径已由下面的 jumpActions 承担，不要在这里改写成 chat/agent 动作名。
        String kind = UiBlockValidator.text(raw, "target");
        boolean group = "group".equals(kind);
        block.put("target", group ? "group" : "agent");

        // 群聊头像不是单一图标，而是成员图标堆叠（前端 GroupAvatar 按 2/3/4/5+ 自动排版）。
        // 只有群聊带 members；普通智能体带 icon（单一 BotIcon）。
        if (group) {
            List<Map<String, Object>> members = members(raw.get("members"));
            if (!members.isEmpty()) {
                block.put("members", members);
            }
        } else {
            String icon = iconJson(raw);
            if (icon != null) {
                block.put("icon", icon);
            }
        }

        List<Map<String, String>> actions = new java.util.ArrayList<>();
        actions.add(action("开始对话", "/chat/" + id));
        if (!group) {
            actions.add(action("编辑配置", "/dashboard/agent/" + id));
        }
        // 用 jumpActions 而非 actions：actions 在块协议里是"提示词按钮"的固定字段，重名会互相干扰
        block.put("jumpActions", actions);
        return block;
    }

    /** 群成员：id + name + icon（icon 是成员自己的规范 JSON，GroupAvatar 解析失败会回退首字） */
    private static List<Map<String, Object>> members(JsonNode raw) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (raw == null || !raw.isArray()) {
            return result;
        }
        for (JsonNode item : raw) {
            if (result.size() >= 10 || !item.isObject()) {
                continue;
            }
            String memberId = UiBlockValidator.text(item, "id");
            if (memberId == null) {
                continue;
            }
            Map<String, Object> member = new LinkedHashMap<>();
            member.put("id", memberId);
            String memberName = UiBlockValidator.text(item, "name");
            if (memberName != null) {
                member.put("name", memberName);
            }
            // 单个成员图标非法只丢这个成员的 icon（前端回退首字方块），
            // 不能让整张卡片失败——否则一个老数据成员就能挡掉整群预览
            String icon;
            try {
                icon = iconJson(item);
            } catch (IllegalArgumentException ignored) {
                icon = null;
            }
            if (icon != null) {
                member.put("icon", icon);
            }
            result.add(member);
        }
        return result;
    }

    /** 图标走结构化 shape+color → 规范 JSON；两者都没给时不输出（前端回退默认图标） */
    private static String iconJson(JsonNode raw) {
        String shape = UiBlockValidator.text(raw, "shape");
        String color = UiBlockValidator.text(raw, "color");
        if (shape == null && color == null) {
            return null;
        }
        try {
            return AgentIconSpec.toJson(shape, color);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("agent_card icon: " + e.getMessage());
        }
    }

    private static Map<String, String> action(String label, String path) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("path", path);
        return item;
    }
}
