package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>可靠性级别枚举</p>
 *
 * @author goya
 * @since 2025/6/26 22:00
 */
public enum ReliabilityLevel {

    /**
     * 发后不管 - 性能最高，但可能丢失消息
     */
    FIRE_AND_FORGET,
    /**
     * 可靠投递 - 确保消息最终投递成功，使用 Outbox 模式
     */
    RELIABLE,
    /**
     * 事务消息 - 与本地事务绑定，事务提交时消息才发送
     */
    TRANSACTIONAL
}
