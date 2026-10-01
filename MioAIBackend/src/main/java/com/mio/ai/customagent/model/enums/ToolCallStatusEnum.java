package com.mio.ai.customagent.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 工具调用状态枚举
 */
@Getter
public enum ToolCallStatusEnum {

    SUCCESS(1, "成功"),
    FAILED(0, "失败"),
    TIMEOUT(2, "超时");

    private final int code;
    private final String desc;

    ToolCallStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ToolCallStatusEnum getByCode(int code) {
        for (ToolCallStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
