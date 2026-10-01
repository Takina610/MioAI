package com.mio.ai.customagent.model.enums;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档处理状态枚举
 */
@Getter
public enum DocumentStatusEnum {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "处理失败");

    private final int code;
    private final String desc;

    DocumentStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DocumentStatusEnum getByCode(int code) {
        for (DocumentStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
