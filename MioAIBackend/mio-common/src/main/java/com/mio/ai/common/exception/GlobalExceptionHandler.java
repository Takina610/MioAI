package com.mio.ai.common.exception;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.ResultUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;

import java.util.stream.Collectors;

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
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("参数校验失败, e: {}", msg);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, msg);
    }

    @ExceptionHandler
    public BaseResponse<?> exceptionHandler(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.error("参数校验失败, e: {}", msg);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, msg);
    }

    /**
     * 文件上传请求缺少 multipart 表单（多为前端 Content-Type 被覆盖），给明确提示而非系统错误
     */
    @ExceptionHandler
    public BaseResponse<?> exceptionHandler(MultipartException e) {
        log.error("multipart 请求解析失败, e: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "文件上传请求格式错误，请重新选择文件后上传");
    }

    /**
     * 缺少必填查询参数：属调用方参数错误，不能落到兜底的「系统错误」——
     * 否则前端无法区分"参数没传对"与"服务端故障"。
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public BaseResponse<?> exceptionHandler(
            org.springframework.web.bind.MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "缺少参数: " + e.getParameterName());
    }

    /** 路径/查询参数类型不匹配（如给数字传了非数字） */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> exceptionHandler(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}={}", e.getName(), e.getValue());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "参数类型不正确: " + e.getName());
    }

    /** 请求体缺失或 JSON 解析失败 */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public BaseResponse<?> exceptionHandler(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体格式不正确");
    }

    /** 请求方法不支持（GET 打到了只接受 POST 的路径） */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public BaseResponse<?> exceptionHandler(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 路径不匹配任何路由（如 /agents/-1 不满足 {id:\d+}，或 /agents/1 OR 1=1 这类注入串）：
     * 属「资源不存在/请求不合法」，不能落到兜底的「系统错误」。
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public BaseResponse<?> exceptionHandler(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "请求的路径不存在");
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
