package com.ysmjjsy.goya.security.bus.serializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * JSON 消息序列化器
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
public class JsonMessageSerializer implements MessageSerializer {

    private final ObjectMapper objectMapper;

    public JsonMessageSerializer() {
        this.objectMapper = createObjectMapper();
    }

    private ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                // 基础配置
                .addModule(new JavaTimeModule())
                .addModule(new ParameterNamesModule())

                // 序列化配置
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

                // 反序列化配置
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)

                // 字段访问配置
                .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)

                .build();
    }


    @Override
    public byte[] serialize(Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to serialize object: {}", object, e);
            throw new RuntimeException("Serialization failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize data to class: {}", clazz.getName(), e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }
} 