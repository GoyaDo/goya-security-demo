package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>事件类型枚举</p>
 *
 * @author goya
 * @since 2025/6/26 22:00
 */
public enum EventType {

    /**
     * 普通事件 - 立即发送
     */
    NORMAL,
    /**
     * 延迟事件 - 指定延迟时长后发送
     */
    DELAYED,
    /**
     * 定时事件 - 指定时间点发送
     */
    SCHEDULED,
    /**
     * 顺序事件 - 按指定键值顺序消费
     */
    ORDERED
}
