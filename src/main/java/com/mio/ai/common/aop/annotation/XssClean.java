package com.mio.ai.common.aop.annotation;

import java.lang.annotation.*;

/**
 * 标记字段需要进行 XSS 清理
 * 配合 Jackson 反序列化器在对象转换时自动过滤危险内容
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XssClean {

    /**
     * 清理模式：rich（保留安全HTML标签）/ strict（只保留基础标签）/ escape（完全转义）
     */
    String mode() default "rich";
}
