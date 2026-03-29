package com.mio.ai.common.common;

import com.mio.ai.common.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/3/28 11:15
 * @description: 全局响应封装类
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
