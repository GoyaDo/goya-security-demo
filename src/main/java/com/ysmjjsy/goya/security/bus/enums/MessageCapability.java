package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>消息能力枚举</p>
 * 表示传输层支持的特性
 * @author goya
 * @since 2025/6/26 22:48
 */
public enum MessageCapability {

    /**
     * 延迟消息支持
     */
    DELAYED_MESSAGE,

    /**
     * 事务消息支持
     */
    TRANSACTIONAL_MESSAGE,

    /**
     * 顺序消息支持
     */
    ORDERED_MESSAGE,

    /**
     * 批量消息支持
     */
    BATCH_MESSAGE,

    /**
     * 消息持久化支持
     */
    PERSISTENT_MESSAGE,

    /**
     * 死信队列支持
     */
    DEAD_LETTER_QUEUE,

    /**
     * 消息追踪支持
     */
    MESSAGE_TRACING,

    /**
     * 集群支持
     */
    CLUSTER_SUPPORT
}
