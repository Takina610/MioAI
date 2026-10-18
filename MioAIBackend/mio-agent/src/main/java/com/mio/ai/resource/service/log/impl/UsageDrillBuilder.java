package com.mio.ai.resource.service.log.impl;

import com.mio.ai.resource.model.vo.log.UsageAnalyticsVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用构成构建：一级为分类（智能体 / 知识库 / MCP 工具 / 技能），
 * 二级为细分（智能体按模型拆，其余按智能体拆），超量项合并为"其他"。
 */
final class UsageDrillBuilder {

    private static final int CATEGORY_LIMIT = 12;
    private static final int SUB_LIMIT = 8;

    private UsageDrillBuilder() {
    }

    static List<UsageAnalyticsVO.DrillNode> build(String type, List<UsageRow> rows) {
        Map<String, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (UsageRow row : rows) {
            boolean byAgent = "agent".equals(type);
            String category = byAgent ? row.agentName() : row.targetName();
            String sub = byAgent ? row.model() : row.agentName();
            if (category == null || category.isBlank()) {
                category = "未知";
            }
            if (sub == null || sub.isBlank()) {
                sub = "未知";
            }
            grouped.computeIfAbsent(category, ignored -> new LinkedHashMap<>())
                    .merge(sub, row.weight(), Long::sum);
        }

        List<Map.Entry<String, Map<String, Long>>> sorted = grouped.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, Map<String, Long>> entry) -> sum(entry.getValue())).reversed())
                .toList();

        List<UsageAnalyticsVO.DrillNode> result = new ArrayList<>();
        Map<String, Long> restSubs = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Map<String, Long>> entry = sorted.get(i);
            if (i < CATEGORY_LIMIT) {
                result.add(buildCategory(entry.getKey(), entry.getValue()));
            } else {
                entry.getValue().forEach((sub, value) -> restSubs.merge(sub, value, Long::sum));
            }
        }
        if (!restSubs.isEmpty()) {
            result.add(buildCategory(grouped.containsKey("其他") ? "其他资源" : "其他", restSubs));
        }
        return result;
    }

    private static UsageAnalyticsVO.DrillNode buildCategory(String name, Map<String, Long> subs) {
        List<Map.Entry<String, Long>> sorted = subs.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        List<UsageAnalyticsVO.DrillNode> children = new ArrayList<>();
        long rest = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            if (i < SUB_LIMIT) {
                children.add(node("c:" + name + ":" + entry.getKey(), entry.getKey(), entry.getValue()));
            } else {
                rest += entry.getValue();
            }
        }
        if (rest > 0) {
            children.add(node("c:" + name + ":其他", "其他", rest));
        }

        UsageAnalyticsVO.DrillNode parent = node("c:" + name, name, sum(subs));
        parent.setChildren(children);
        return parent;
    }

    private static UsageAnalyticsVO.DrillNode node(String id, String name, long value) {
        UsageAnalyticsVO.DrillNode node = new UsageAnalyticsVO.DrillNode();
        node.setId(id);
        node.setName(name);
        node.setValue(value);
        return node;
    }

    private static long sum(Map<String, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
    }
}
