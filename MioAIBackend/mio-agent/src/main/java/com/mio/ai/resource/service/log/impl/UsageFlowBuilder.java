package com.mio.ai.resource.service.log.impl;

import com.mio.ai.resource.model.vo.log.UsageAnalyticsVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用去向构建：用户 → 智能体 → 目标（模型/知识库/MCP 工具/技能）。
 * 每层按权重保留 Top N，其余合并为"其他"，避免桑基图节点爆炸。
 */
final class UsageFlowBuilder {

    private static final int USER_LIMIT = 6;
    private static final int AGENT_LIMIT = 8;
    private static final int TARGET_LIMIT = 6;
    /** 低于总量 3% 的节点并入"其他"：桑基节点高度按占比分配，过小节点标签必然重叠 */
    private static final double MIN_SHARE = 0.03;

    private UsageFlowBuilder() {
    }

    static UsageAnalyticsVO.Flow build(String type, List<UsageRow> rows,
                                       Map<Long, String> userNames, boolean userLayer) {
        UsageAnalyticsVO.Flow flow = new UsageAnalyticsVO.Flow();
        flow.setNodes(new ArrayList<>());
        flow.setLinks(new ArrayList<>());
        if (rows.isEmpty()) {
            return flow;
        }

        Map<String, Long> userTotals = new HashMap<>();
        Map<String, Long> agentTotals = new HashMap<>();
        Map<String, Long> targetTotals = new HashMap<>();
        Map<String, String> userDisplays = new HashMap<>();
        Map<String, String> agentDisplays = new HashMap<>();
        Map<String, String> targetDisplays = new HashMap<>();

        for (UsageRow row : rows) {
            String userKey = userKey(row);
            String agentKey = agentKey(row);
            String targetKey = targetKey(row);
            if (userLayer) {
                userTotals.merge(userKey, row.weight(), Long::sum);
                userDisplays.putIfAbsent(userKey, userDisplay(row, userNames));
            }
            agentTotals.merge(agentKey, row.weight(), Long::sum);
            agentDisplays.putIfAbsent(agentKey, row.agentName());
            targetTotals.merge(targetKey, row.weight(), Long::sum);
            targetDisplays.putIfAbsent(targetKey, row.targetName());
        }

        Map<String, String> keptUsers = keepOrOther(userTotals, USER_LIMIT, "u:other");
        Map<String, String> keptAgents = keepOrOther(agentTotals, AGENT_LIMIT, "a:other");
        Map<String, String> keptTargets = keepOrOther(targetTotals, TARGET_LIMIT, "t:other");
        disambiguateUserNames(userDisplays);

        Map<String, UsageAnalyticsVO.FlowLink> links = new HashMap<>();
        for (UsageRow row : rows) {
            String agentKey = keptAgents.get(agentKey(row));
            String targetKey = keptTargets.get(targetKey(row));
            if (userLayer) {
                addLink(links, keptUsers.get(userKey(row)), agentKey, row.weight());
            }
            addLink(links, agentKey, targetKey, row.weight());
        }

        Map<String, Long> values = new HashMap<>();
        for (UsageAnalyticsVO.FlowLink link : links.values()) {
            values.merge(link.getSource(), link.getValue(), Long::sum);
            values.merge(link.getTarget(), link.getValue(), Long::sum);
        }

        String targetOther = targetOtherLabel(type);
        int agentLayer = userLayer ? 1 : 0;
        int targetLayer = userLayer ? 2 : 1;
        List<UsageAnalyticsVO.FlowNode> nodes = new ArrayList<>(values.size());
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            String key = entry.getKey();
            UsageAnalyticsVO.FlowNode node = new UsageAnalyticsVO.FlowNode();
            node.setName(key);
            node.setValue(entry.getValue());
            if (key.startsWith("u:")) {
                node.setLayer(0);
                node.setDisplayName("u:other".equals(key) ? "其他用户"
                        : userDisplays.getOrDefault(key, key.substring(2)));
            } else if (key.startsWith("a:")) {
                node.setLayer(agentLayer);
                node.setDisplayName("a:other".equals(key) ? "其他智能体"
                        : agentDisplays.getOrDefault(key, key.substring(2)));
            } else {
                node.setLayer(targetLayer);
                node.setDisplayName("t:other".equals(key) ? targetOther
                        : targetDisplays.getOrDefault(key, key.substring(2)));
            }
            nodes.add(node);
        }
        nodes.sort(Comparator.comparingInt(UsageAnalyticsVO.FlowNode::getLayer)
                .thenComparing(Comparator.comparingLong(UsageAnalyticsVO.FlowNode::getValue).reversed()));

        List<UsageAnalyticsVO.FlowLink> linkList = new ArrayList<>(links.values());
        linkList.sort(Comparator.comparingLong(UsageAnalyticsVO.FlowLink::getValue).reversed());

        flow.setNodes(nodes);
        flow.setLinks(linkList);
        return flow;
    }

    /** 原 key → 保留 key 或 otherKey 的映射（每行都参与，保证"其他"聚合完整） */
    private static Map<String, String> keepOrOther(Map<String, Long> totals, int limit, String otherKey) {
        long total = totals.values().stream().mapToLong(Long::longValue).sum();
        List<String> sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        int keepCount = Math.min(limit, sorted.size());
        List<String> kept = sorted.subList(0, keepCount).stream()
                .filter(key -> total == 0 || totals.get(key) >= total * MIN_SHARE)
                .toList();
        Map<String, String> mapping = new HashMap<>();
        for (String key : totals.keySet()) {
            mapping.put(key, kept.contains(key) ? key : otherKey);
        }
        return mapping;
    }

    private static void addLink(Map<String, UsageAnalyticsVO.FlowLink> links,
                                String source, String target, long value) {
        if (source == null || target == null || value <= 0) {
            return;
        }
        String key = source + "\u0000" + target;
        UsageAnalyticsVO.FlowLink link = links.computeIfAbsent(key, ignored -> {
            UsageAnalyticsVO.FlowLink created = new UsageAnalyticsVO.FlowLink();
            created.setSource(source);
            created.setTarget(target);
            return created;
        });
        link.setValue(link.getValue() + value);
    }

    private static String userKey(UsageRow row) {
        return "u:" + (row.userId() != null ? row.userId() : "guest");
    }

    private static String agentKey(UsageRow row) {
        // 按展示名归并：同一用户可能持有多个同名 MioBot 副本，流向图里不该出现多个同名节点
        return "a:" + row.agentName();
    }

    private static String targetKey(UsageRow row) {
        return "t:" + row.targetName();
    }

    private static String userDisplay(UsageRow row, Map<Long, String> userNames) {
        if (row.userId() == null) {
            return "游客";
        }
        return userNames.getOrDefault(row.userId(), "用户 " + row.userId());
    }

    /** 同名用户追加 ID 区分（多个"无名"在流向图里无法辨认） */
    private static void disambiguateUserNames(Map<String, String> userDisplays) {
        Map<String, Long> nameCounts = userDisplays.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        name -> name, java.util.stream.Collectors.counting()));
        userDisplays.replaceAll((key, name) -> {
            if (nameCounts.getOrDefault(name, 0L) <= 1) {
                return name;
            }
            String id = key.startsWith("u:") ? key.substring(2) : key;
            return name + " · " + id;
        });
    }

    private static String targetOtherLabel(String type) {
        return switch (type) {
            case "knowledge" -> "其他知识库";
            case "mcp" -> "其他工具";
            case "skill" -> "其他技能";
            default -> "其他模型";
        };
    }
}
