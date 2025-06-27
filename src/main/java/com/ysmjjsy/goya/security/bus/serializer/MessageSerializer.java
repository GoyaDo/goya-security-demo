package com.ysmjjsy.goya.security.bus.serializer;

/**
 * 消息序列化器接口
 *
 * @author goya
 * @since 2025/6/24
 */
public interface MessageSerializer {

    /**
     * 序列化对象
     *
     * @param object 要序列化的对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object object);

    /**
     * 反序列化对象
     *
     * @param data  字节数组
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] data, Class<T> clazz);
} 