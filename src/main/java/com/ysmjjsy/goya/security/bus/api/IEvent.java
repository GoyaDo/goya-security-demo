package com.ysmjjsy.goya.security.bus.api;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一事件接口 - 所有通过 Unified MQ 传输的消息都必须实现此接口
 *
 * @author goya
 * @since 2025/6/24
 */
public interface IEvent {

    /**
     * 获取事件的全局唯一标识符
     * 用于消息追踪、幂等去重等场景
     *
     * @return 事件ID，应当全局唯一且稳定
     */
    String getEventId();

    /**
     * 获取事件类型
     * 用于消息路由、监听器匹配等场景
     *
     * @return 事件类型，如 "order.created", "payment.failed"
     */
    String getEventType();

    /**
     * 获取事件创建时间
     *
     * @return 事件创建的时间戳
     */
    LocalDateTime getCreateTime();

    /**
     * 获取框架元数据
     * 用于存储框架内部使用的信息，如消息来源、传输配置等
     *
     * @return 元数据映射，不可为null
     */
    Map<String, Object> getMetadata();

    /**
     * 获取业务属性
     * 用于消息过滤、路由等业务场景
     *
     * @return 业务属性映射，不可为null
     */
    Map<String, String> getProperties();

    /**
     * 获取消息优先级
     * 数值越大优先级越高，默认为0
     *
     * @return 消息优先级
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 设置事件ID
     * 
     * @param eventId 事件ID
     */
    void setEventId(String eventId);

    /**
     * 设置事件类型
     * 
     * @param eventType 事件类型
     */
    void setEventType(String eventType);

    /**
     * 设置业务属性
     * 
     * @param key 属性键
     * @param value 属性值
     */
    void setProperty(String key, String value);

    /**
     * 设置框架元数据
     * 
     * @param key 元数据键
     * @param value 元数据值
     */
    void setMetadata(String key, Object value);
} 