package com.mio.ai.customagent.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体类型枚举
 */
@Getter
public enum AgentTypeEnum {

    GENERAL(0, "内置智能体"),
    CUSTOM(1, "自定义智能体");

    private final int code;
    private final String desc;

    AgentTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AgentTypeEnum getByCode(int code) {
        for (AgentTypeEnum type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return null;
    }
}
