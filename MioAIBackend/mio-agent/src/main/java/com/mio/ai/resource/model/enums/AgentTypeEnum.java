package com.mio.ai.resource.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体类型枚举
 */
@Getter
public enum AgentTypeEnum {

    GENERAL(0, "内置智能体"),
    CUSTOM(1, "自定义智能体"),
    /** 多个成员智能体的集合：对话时由调度中枢按任务委派成员执行 */
    GROUP(2, "群聊智能体"),
    /** 注册时为每个用户创建的专属 MioBot 副本（行为同内置 MioBot，可编辑不可删除） */
    MIOBOT(3, "个人MioBot");

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
