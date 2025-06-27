package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>消息类型枚举</p>
 *
 * @author goya
 * @since 2025/6/26 22:00
 */
public enum MessageType {

    /**
     * 普通消息 - 立即发送
     */
    NORMAL,
    /**
     * 延迟消息 - 指定延迟时长后发送
     */
    DELAYED,
    /**
     * 定时消息 - 指定时间点发送
     */
    SCHEDULED,
    /**
     * 顺序消息 - 按指定键值顺序消费
     */
    ORDERED
}
