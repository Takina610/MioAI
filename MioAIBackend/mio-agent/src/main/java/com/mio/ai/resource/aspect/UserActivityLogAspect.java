package com.mio.ai.resource.aspect;

import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.resource.model.entity.UserActivityLog;
import com.mio.ai.resource.service.log.UserActivityLogService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Date;

/**
 * 用户执行日志切面：拦截全部控制器的写操作（POST/PUT/DELETE），记录谁在何时对什么
 * 做了什么、成功与否、耗时多少。读操作不入库（噪声）；登录/注册由事件侧补记。
 */
@Aspect
@Component
@Slf4j
public class UserActivityLogAspect {

    /** 入参摘要截断长度（与 user_activity_log.summary 列宽对齐） */
    private static final int SUMMARY_CAP = 480;

    /** 失败摘要截断长度 */
    private static final int ERROR_CAP = 480;

    @Resource
    private UserActivityLogService userActivityLogService;

    @Resource
    private RedisComponent redisComponent;

    @Pointcut("execution(public * com.mio.ai..controller..*(..))")
    public void controllerWriteOps() {
    }

    @Around("controllerWriteOps()")
    public Object record(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        var method = signature.getMethod();
        boolean writeOp = method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null;

        HttpServletRequest request = currentRequest();
        String httpMethod = request != null ? request.getMethod() : null;
        boolean isWriteHttp = httpMethod != null
                && ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)
                || "DELETE".equalsIgnoreCase(httpMethod));
        if (!writeOp && !isWriteHttp) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            try {
                recordActivity(joinPoint, request, failure, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.debug("执行日志记录失败: {}", e.getMessage());
            }
        }
    }

    private void recordActivity(ProceedingJoinPoint joinPoint, HttpServletRequest request,
                                Throwable failure, long costMs) {
        UserActivityLog row = new UserActivityLog();
        String token = request != null ? request.getHeader("token") : null;
        row.setUserId(token != null ? redisComponent.getUserId(token) : null);
        row.setModule(resolveModule(joinPoint));
        row.setAction(joinPoint.getSignature().getName());
        row.setHttpMethod(request != null ? request.getMethod() : null);
        row.setPath(request != null ? request.getRequestURI() : null);
        row.setSummary(buildSummary(joinPoint.getArgs()));
        row.setSuccess(failure == null ? 1 : 0);
        row.setErrorMsg(failure == null ? null : truncate(String.valueOf(failure.getMessage()), ERROR_CAP));
        row.setCostMs(costMs);
        row.setIp(request != null ? resolveIp(request) : null);
        row.setCreateTime(new Date());
        userActivityLogService.recordAsync(row);
    }

    /** 模块名取控制器包的末段：bot→chat，customagent→agent，resource.controller.X→X */
    private String resolveModule(ProceedingJoinPoint joinPoint) {
        String pkg = joinPoint.getSignature().getDeclaringType().getPackageName();
        String[] segments = pkg.split("\\.");
        String last = segments[segments.length - 1];
        if ("controller".equals(last) && segments.length >= 2) {
            String prev = segments[segments.length - 2];
            return "bot".equals(prev) ? "chat" : prev;
        }
        return "customagent".equals(last) ? "agent" : last;
    }

    /** 入参序列化：跳过 Servlet/MultipartFile/SSE 等不可序列化对象，JSON 截断 */
    private String buildSummary(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] filtered = java.util.Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest)
                        && !(arg instanceof jakarta.servlet.http.HttpServletResponse)
                        && !(arg instanceof MultipartFile)
                        && !(arg instanceof SseEmitter)
                        && !(arg instanceof java.io.InputStream)
                        && !(arg instanceof byte[]))
                .toArray();
        if (filtered.length == 0) {
            return null;
        }
        try {
            return truncate(JacksonUtil.writeValueAsString(filtered), SUMMARY_CAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text, int cap) {
        if (text == null) {
            return null;
        }
        return text.length() <= cap ? text : text.substring(0, cap);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
