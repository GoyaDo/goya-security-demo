package com.ysmjjsy.goya.security.bus.serializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Jackson 的事件序列化器
 *
 * 改进版本，支持：
 * 1. 字段级别的序列化/反序列化
 * 2. 构造器参数名检测
 * 3. 更宽松的反序列化策略
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
@Getter
public class JacksonEventSerializer implements EventSerializer {

    private static final Logger log = LoggerFactory.getLogger(JacksonEventSerializer.class);

    private final ObjectMapper objectMapper;

    public JacksonEventSerializer() {
        this.objectMapper = createObjectMapper();
    }

    public JacksonEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private ObjectMapper createObjectMapper() {
        return JsonMapper.builder()
                // 基础配置
                .addModule(new JavaTimeModule())
                .addModule(new ParameterNamesModule())

                // 序列化配置
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

                // 反序列化配置 - 更宽松的策略
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)

                // 字段访问配置 - 关键改进
                .visibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
                .visibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY)

                .build();
    }

    @Override
    public String serialize(IEvent event) throws SerializationException {
        try {
            String json = objectMapper.writeValueAsString(event);
            log.debug("Serialized event {}: {}", event.getEventId(), json);
            return json;
        } catch (JsonProcessingException e) {
            String errorMsg = String.format("Failed to serialize event: %s, type: %s",
                    event.getEventId(), event.getClass().getSimpleName());
            log.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public <T extends IEvent> T deserialize(String data, Class<T> eventType) throws SerializationException {
        try {
            T event = objectMapper.readValue(data, eventType);
            log.debug("Deserialized event {}: {}", event.getEventId(), event.getEventType());
            return event;
        } catch (JsonProcessingException e) {
            String errorMsg = String.format("Failed to deserialize event data for type: %s, data: %s",
                    eventType.getSimpleName(), data);
            log.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }

    @Override
    public boolean supports(Class<? extends IEvent> eventType) {
        // Jackson 可以序列化所有 IEvent 子类
        return IEvent.class.isAssignableFrom(eventType);
    }
}