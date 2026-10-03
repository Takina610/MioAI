package com.mio.ai.resource.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库状态枚举
 */
@Getter
public enum KnowledgeBaseStatusEnum {

    CREATING(0, "创建中"),
    ACTIVE(1, "正常"),
    DISABLED(2, "已禁用");

    private final int code;
    private final String desc;

    KnowledgeBaseStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static KnowledgeBaseStatusEnum getByCode(int code) {
        for (KnowledgeBaseStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
