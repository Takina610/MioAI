package com.mio.ai.common.aop.Aspect;

import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.service.AdminCheckService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * @author: Takina
 * @date: 2026/3/28 11:24
 * @description:
 */
@Aspect
@Component
public class AuthInterceptor {

    @Autowired
    AdminCheckService adminCheckService;

    @Before("@annotation(com.mio.ai.common.aop.annotation.AuthCheck)")
    public void interceptorDo(JoinPoint joinpoint) {
        if (null == adminCheckService) {
            return;
        }

        // 1. 反射获取被拦截的方法
        Method method = ((MethodSignature) joinpoint.getSignature()).getMethod();

        // 2. 获取方法上的 GlobalInterceptor 注解
        AuthCheck authCheck = method.getAnnotation(AuthCheck.class);

        // 3. 获取当前请求上下文
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        // 4. 校验上下文是否存在
        ThrowUtils.throwIf(null == attributes, ErrorCode.SYSTEM_ERROR);

        // 5. 获取 request
        HttpServletRequest request = attributes.getRequest();

        // 6。 从请求头获取 token
        String token = request.getHeader("token");

        // 7. 获取参数
        String mustRole = authCheck.mustRole();

        // 8. 验证
        adminCheckService.validateUser(mustRole, token);
    }
}
