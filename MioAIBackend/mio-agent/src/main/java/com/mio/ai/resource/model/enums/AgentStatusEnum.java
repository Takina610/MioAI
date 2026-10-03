package com.mio.ai.resource.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体状态枚举
 */
@Getter
public enum AgentStatusEnum {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    DISABLED(2, "已禁用");

    private final int code;
    private final String desc;

    AgentStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AgentStatusEnum getByCode(int code) {
        for (AgentStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
