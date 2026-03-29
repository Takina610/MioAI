package com.mio.ai.common.aop.Aspect;

import cn.hutool.http.server.HttpServerRequest;
import com.mio.ai.common.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/3/28 15:25
 * @description: AOP 日志切面：拦截 @LogInfo 注解的方法，自动打印日志
 */
@Aspect
@Component
@Slf4j
public class LogInfoAspect {
    /**
     * 前置通知：方法执行前打印日志
     * 切点：所有添加了 @LogInfo 注解的方法
     */
    @Before("@annotation(com.mio.ai.common.aop.annotation.LogInfo)")
    public void logMethodInfo(JoinPoint joinPoint) {
        // 1. 获取方法名
        String methodName = joinPoint.getSignature().getName();

        // 2. 获取方法入参
        Object[] params = joinPoint.getArgs();

        // 3. 序列化参数
        String serializableParams = "";
        if (params != null && params.length > 0 && params[0] instanceof HttpServerRequest) {
            serializableParams = JacksonUtil.writeValueAsString(params[0]);
        }
        // 4. 打印日志
        assert params != null;
        if (params.length == 0) {
            // 无参方法
            log.info("methodName: {} Parameter: {}", methodName, "无参数");
        } else {
            // 有参方法（支持多个参数，自动拼接）
            log.info("methodName: {} Parameter: {}", methodName, serializableParams);
        }
    }
}
