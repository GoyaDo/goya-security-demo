package com.ysmjjsy.goya.security.bus.api;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ysmjjsy.goya.security.bus.enums.EventStatus;

import java.time.LocalDateTime;

/**
 * 统一事件接口 - 所有通过 Unified MQ 传输的消息都必须实现此接口
 *
 * @author goya
 * @since 2025/6/24
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
public interface IEvent {

    /**
     * 获取事件的全局唯一标识符
     * 用于消息追踪、幂等去重等场景
     *
     * @return 事件ID，应当全局唯一且稳定
     */
    String getEventId();

    /**
     * 获取事件key
     * 用于消息路由、监听器匹配等场景
     *
     * @return 事件类型，如 "order.created", "payment.failed"
     */
    String getEventKey();

    /**
     * 获取事件创建时间
     *
     * @return 事件创建的时间戳
     */
    LocalDateTime getCreateTime();

    /**
     * 获取事件状态
     * @return 事件状态
     */
    EventStatus getEventStatus();

    /**
     * 设置事件状态
     * @param eventStatus 事件状态
     */
    void setEventStatus(EventStatus eventStatus);

    /**
     * 获取消息优先级
     * 数值越大优先级越高，默认为0
     *
     * @return 消息优先级
     */
    default int getPriority() {
        return 0;
    }
} 