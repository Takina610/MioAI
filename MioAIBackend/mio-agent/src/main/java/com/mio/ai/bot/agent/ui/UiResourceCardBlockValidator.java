package com.mio.ai.bot.agent.ui;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * resource_card 块校验：MCP / 知识库创建后的预览卡片（可跳转到对应管理页）。
 * <p>与 {@link UiAgentCardBlockValidator} 同一思路——跳转路径由后端拼，
 * id 必须是真实数字主键，模型编造 id 时当场失败。
 */
final class UiResourceCardBlockValidator {

    private static final Pattern DIGITS = Pattern.compile("^\\d{1,18}$");
    private static final Set<String> TYPES = Set.of("mcp", "knowledge");

    private UiResourceCardBlockValidator() {
    }

    static Map<String, Object> validate(JsonNode raw) {
        Map<String, Object> block = new LinkedHashMap<>();

        String name = UiBlockValidator.text(raw, "name");
        if (name == null) {
            throw new IllegalArgumentException("resource_card needs a non-empty name");
        }
        block.put("name", name);

        String id = UiBlockValidator.text(raw, "id");
        if (id == null || !DIGITS.matcher(id).matches()) {
            throw new IllegalArgumentException("resource_card id must be a real numeric id, got '" + id
                    + "' — create the resource first and use the id from its tool result");
        }
        block.put("resourceId", id);

        String type = UiBlockValidator.text(raw, "resourceType");
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("resource_card resourceType must be mcp or knowledge");
        }
        block.put("resourceType", type);

        String description = UiBlockValidator.text(raw, "description");
        if (description != null) {
            block.put("description", description);
        }
        String status = UiBlockValidator.text(raw, "status");
        if (status != null) {
            block.put("status", status);
        }
        String detail = UiBlockValidator.text(raw, "detail");
        if (detail != null) {
            block.put("detail", detail);
        }

        // 跳转路径由后端决定：MCP 进配置页，知识库进详情页并直接打开上传步骤
        List<Map<String, String>> actions = new ArrayList<>();
        if ("mcp".equals(type)) {
            actions.add(action("查看配置", "/dashboard/mcp/" + id));
            actions.add(action("去装配", "/dashboard/agent"));
        } else {
            // upload=1 让详情页挂载即打开上传弹框（否则用户还要再点一次）
            actions.add(action("上传文档", "/dashboard/knowledge/" + id + "?upload=1"));
            actions.add(action("查看知识库", "/dashboard/knowledge"));
        }
        block.put("jumpActions", actions);
        return block;
    }

    private static Map<String, String> action(String label, String path) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("path", path);
        return item;
    }
}
