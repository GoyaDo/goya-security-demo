package com.ysmjjsy.goya.security.bus.serializer;


import com.ysmjjsy.goya.security.bus.domain.IEvent;

/**
 * 事件序列化器接口
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
public interface EventSerializer {

    /**
     * 序列化事件
     * 
     * @param event 要序列化的事件
     * @return 序列化后的字符串
     * @throws SerializationException 序列化异常
     */
    String serialize(IEvent event) throws SerializationException;

    /**
     * 反序列化事件
     * 
     * @param data 序列化的数据
     * @param eventType 事件类型
     * @return 反序列化后的事件
     * @throws SerializationException 反序列化异常
     */
    <T extends IEvent> T deserialize(String data, Class<T> eventType) throws SerializationException;

    /**
     * 检查是否支持指定的事件类型
     * 
     * @param eventType 事件类型
     * @return 是否支持
     */
    boolean supports(Class<? extends IEvent> eventType);
}