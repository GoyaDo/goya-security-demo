package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>事件能力枚举</p>
 * 表示传输层支持的特性
 * @author goya
 * @since 2025/6/26 22:48
 */
public enum EventCapability {

    /**
     * 延迟事件支持
     */
    DELAYED_MESSAGE,

    /**
     * 事务事件支持
     */
    TRANSACTIONAL_MESSAGE,

    /**
     * 顺序事件支持
     */
    ORDERED_MESSAGE,

    /**
     * 批量事件支持
     */
    BATCH_MESSAGE,

    /**
     * 事件持久化支持
     */
    PERSISTENT_MESSAGE,

    /**
     * 死信队列支持
     */
    DEAD_LETTER_QUEUE,

    /**
     * 事件追踪支持
     */
    MESSAGE_TRACING,

    /**
     * 集群支持
     */
    CLUSTER_SUPPORT
}
