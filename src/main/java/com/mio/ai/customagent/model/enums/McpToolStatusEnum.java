package com.mio.ai.customagent.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具状态枚举
 */
@Getter
public enum McpToolStatusEnum {

    INACTIVE(0, "未激活"),
    ACTIVE(1, "正常"),
    ERROR(2, "异常");

    private final int code;
    private final String desc;

    McpToolStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static McpToolStatusEnum getByCode(int code) {
        for (McpToolStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
