package com.mio.ai.resource.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能状态枚举
 */
@Getter
public enum SkillStatusEnum {

    DISABLED(0, "已禁用"),
    ACTIVE(1, "正常");

    private final int code;
    private final String desc;

    SkillStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static SkillStatusEnum getByCode(int code) {
        for (SkillStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
