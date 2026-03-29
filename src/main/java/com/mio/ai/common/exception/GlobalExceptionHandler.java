package com.mio.ai.common.exception;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * @author: Takina
 * @date: 2026/3/28 11:13
 * @description: 全局异常处理器
 */

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler
    public BaseResponse<?> exceptionHandler(HandlerMethodValidationException e) {
        log.error("发生异常，e：{}", e.getMessage());
        return ResultUtils.error(ErrorCode.CLIENT_ERROR, e.getMessage());
    }

    @ExceptionHandler
    public BaseResponse<?> exceptionHandler(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError().getDefaultMessage();
        log.error("发生异常, e: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.CLIENT_ERROR, e.getMessage());
    }

    @ExceptionHandler
    public BaseResponse<?> exceptionHandler(Exception e) {
        log.error("发生异常，e：", e);
        return ResultUtils.error(ErrorCode.UNKNOWN_ERROR, "系统错误");
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> businessExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
