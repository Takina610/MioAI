package com.mio.ai.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.mio.ai.common.utils.XssUtils;

import java.io.IOException;

/**
 * XSS 清理 Jackson 反序列化器
 * 对标记了 @XssClean 的字符串字段自动进行 XSS 过滤
 */
public class XssCleanJsonDeserializer extends JsonDeserializer<String> {

    private final String mode;

    public XssCleanJsonDeserializer() {
        this.mode = "rich";
    }

    public XssCleanJsonDeserializer(String mode) {
        this.mode = mode;
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (mode) {
            case "strict" -> XssUtils.cleanStrict(value);
            case "escape" -> XssUtils.escapeHtml(value);
            default -> XssUtils.cleanRichText(value);
        };
    }
}
