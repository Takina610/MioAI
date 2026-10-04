package com.mio.ai.framework.zagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.core.util.StrUtil;

/**
 * 工具参数读取助手：宽容取值（zcode 语义化布尔、数字字符串兼容）。
 */
public final class Args {

    private Args() {
    }

    public static String str(JsonNode input, String field) {
        if (input == null || !input.hasNonNull(field)) {
            return null;
        }
        String value = input.get(field).asText();
        return StrUtil.isBlank(value) ? null : value;
    }

    public static Integer intVal(JsonNode input, String field) {
        String raw = str(input, field);
        if (raw == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** zcode 语义化布尔：true/1/yes/y/on（大小写不敏感） */
    public static boolean bool(JsonNode input, String field) {
        String raw = str(input, field);
        if (raw == null) {
            return false;
        }
        return switch (raw.trim().toLowerCase()) {
            case "true", "1", "yes", "y", "on" -> true;
            default -> false;
        };
    }
}
