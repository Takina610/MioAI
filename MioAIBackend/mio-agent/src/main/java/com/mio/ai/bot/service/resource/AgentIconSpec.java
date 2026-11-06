package com.mio.ai.bot.service.resource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体图标规格：把「形体 + 配色」两个结构化参数转成 agent.icon 的 JSON。
 * <p>为什么不让模型直接拼 JSON：{@code agent.icon} 是无后端校验的自由文本，
 * 前端 {@code parseBotIcon} 对非法值静默回退默认图标——模型写错时用户看到的是
 * "设置没生效"而非报错。这里把可选值收敛成枚举，越界直接报可自纠的原因。
 * <p>取值与前端 `components/agent/bot-icon/types.ts` 的 BOT_SHAPES / BOT_PALETTE 同源；
 * 新增形体或配色时两处必须同步（前端有 18 种形体，创建流程只暴露 8 种易懂的）。
 */
public final class AgentIconSpec {

    /** 创建流程可选形体：id → 中文名（与前端 BOT_SHAPES 一致） */
    private static final Map<String, String> SHAPES = new LinkedHashMap<>();

    /** 配色：id → 中文名（与前端 BOT_PALETTE 一致） */
    private static final Map<String, String> PALETTE = new LinkedHashMap<>();

    static {
        SHAPES.put("blob", "团子");
        SHAPES.put("squircle", "方圆");
        SHAPES.put("hex", "六角");
        SHAPES.put("gem", "宝石");
        SHAPES.put("cloud", "云朵");
        SHAPES.put("teardrop", "水滴");
        SHAPES.put("capsule", "胶囊");
        SHAPES.put("leaf", "叶片");

        PALETTE.put("cyan", "青绿");
        PALETTE.put("blue", "蓝色");
        PALETTE.put("violet", "紫罗兰");
        PALETTE.put("green", "绿色");
        PALETTE.put("orange", "橙色");
        PALETTE.put("yellow", "黄色");
        PALETTE.put("red", "红色");
        PALETTE.put("magenta", "品红");
        PALETTE.put("brown", "棕色");
        PALETTE.put("gray", "灰色");
        PALETTE.put("black", "黑色");
    }

    public static final String DEFAULT_SHAPE = "blob";
    public static final String DEFAULT_COLOR = "black";

    /**
     * 问答卡片用的推荐配色子集：AskUserQuestion 每题最多 4 个选项，
     * 从 11 色里挑 4 个语义区分度最高的（青绿=专业、橙=活力、紫=精致、绿=自然）。
     */
    private static final List<String> SUGGESTED_COLORS = List.of("cyan", "orange", "violet", "green");

    private AgentIconSpec() {
    }

    public static List<String> shapeIds() {
        return List.copyOf(SHAPES.keySet());
    }

    public static List<String> colorIds() {
        return List.copyOf(PALETTE.keySet());
    }

    public static String shapeLabel(String shape) {
        return SHAPES.getOrDefault(shape, shape);
    }

    public static String colorLabel(String color) {
        return PALETTE.getOrDefault(color, color);
    }

    public static boolean isValidShape(String shape) {
        return shape != null && SHAPES.containsKey(shape);
    }

    public static boolean isValidColor(String color) {
        return color != null && PALETTE.containsKey(color);
    }

    /**
     * 形体 + 配色 → agent.icon 的 JSON。任一为空走默认值（调用方仅在用户明确指定时传值）。
     * <p>越界不静默纠正：报错让模型或用户看到真实可选集合。
     */
    public static String toJson(String shape, String color) {
        String resolvedShape = blankTo(shape, DEFAULT_SHAPE);
        String resolvedColor = blankTo(color, DEFAULT_COLOR);
        if (!isValidShape(resolvedShape)) {
            throw new IllegalArgumentException("不支持的图标形体：" + shape
                    + "（可选：" + String.join("/", shapeIds()) + "）");
        }
        if (!isValidColor(resolvedColor)) {
            throw new IllegalArgumentException("不支持的图标配色：" + color
                    + "（可选：" + String.join("/", colorIds()) + "）");
        }
        return "{\"shape\":\"" + resolvedShape + "\",\"fill\":{\"kind\":\"palette\",\"id\":\""
                + resolvedColor + "\"}}";
    }

    /** 形体+配色的中文描述，用于回执与预览卡片 */
    public static String describe(String shape, String color) {
        return colorLabel(blankTo(color, DEFAULT_COLOR)) + " · " + shapeLabel(blankTo(shape, DEFAULT_SHAPE))
                + "形";
    }

    /** 推荐配色（问答卡片的选项来源） */
    public static List<String> suggestedColors() {
        return SUGGESTED_COLORS;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
