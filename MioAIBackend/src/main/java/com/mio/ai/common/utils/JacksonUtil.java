package com.mio.ai.common.utils;

import org.springframework.boot.json.JsonParseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author: Takina
 * @date: 2026/3/28 15:40
 * @description: 序列化与反序列化工具（Jackson 3 / tools.jackson）
 */

public class JacksonUtil {
    private JacksonUtil() {

    }

    // ! 单例模式
    private static final JsonMapper JSON_MAPPER;

    static {
        JSON_MAPPER = JsonMapper.builder().build();
    }

    private static JsonMapper getObjectMapper() {
        return JSON_MAPPER;
    }

    /**
     * 序列化方法
     * @Param Object
     * @return String
     */
    public static String writeValueAsString(Object object) {
        return tryParse(() -> JacksonUtil.getObjectMapper().writeValueAsString(object));
    }

    /**
     * 反序列化方法
     * @param serialize
     * @param valueType
     * @return <T>
     */
    public static <T> T readValue(String serialize, Class<T> valueType) {
        return tryParse(() -> JacksonUtil.getObjectMapper().readValue(serialize, valueType));
    }

    public static <T> T readListValue(String serialize, Class<?> valueType) {
        JavaType javaType = JacksonUtil.getObjectMapper().getTypeFactory().constructParametricType(List.class, valueType);
        return tryParse(() -> JacksonUtil.getObjectMapper().readValue(serialize, javaType));
    }

    private static <T> T tryParse(Callable<T> parser) {
        return tryParse(parser, JacksonException.class);
    }

    private static <T> T tryParse(Callable<T> parser, Class<? extends Exception> check) {
        try {
            return parser.call();
        } catch (Exception e) {
            if (check.isAssignableFrom(e.getClass())) {
                throw new JsonParseException(e);
            }
            throw new IllegalStateException(e);
        }
    }
}
