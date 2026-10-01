package com.mio.ai.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mio.ai.common.aop.annotation.XssClean;
import com.mio.ai.common.utils.XssUtils;

import java.io.IOException;

/**
 * XSS 清理 Jackson 模块
 * 自动为标记了 @XssClean 的 String 字段注册清理反序列化器
 */
public class XssCleanJacksonModule extends SimpleModule {

    public XssCleanJacksonModule() {
        super("XssCleanModule");
        // 注册一个通用的 ContextualDeserializer，它会根据字段上的注解决定清理模式
        addDeserializer(String.class, new XssCleanContextualDeserializer());
    }

    /**
     * 上下文感知反序列化器，根据字段上的 @XssClean 注解决定行为
     */
    public static class XssCleanContextualDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

        private String mode = null;

        public XssCleanContextualDeserializer() {
        }

        private XssCleanContextualDeserializer(String mode) {
            this.mode = mode;
        }

        @Override
        public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
            if (property != null) {
                XssClean annotation = property.getAnnotation(XssClean.class);
                if (annotation != null) {
                    return new XssCleanContextualDeserializer(annotation.mode());
                }
            }
            // 没有注解时使用默认的 String 反序列化器
            return new StringDeserializer();
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isEmpty() || mode == null) {
                return value;
            }
            return switch (mode) {
                case "strict" -> XssUtils.cleanStrict(value);
                case "escape" -> XssUtils.escapeHtml(value);
                default -> XssUtils.cleanRichText(value);
            };
        }
    }
}
