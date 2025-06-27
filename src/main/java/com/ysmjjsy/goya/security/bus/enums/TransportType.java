package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>传输层类型枚举</p>
 *
 * @author goya
 * @since 2025/6/26 22:01
 */
public enum TransportType {

    /**
     * RabbitMQ 传输层
     */
    RABBITMQ,
    /**
     * Apache Kafka 传输层
     */
    KAFKA,
    /**
     * Apache RocketMQ 传输层
     */
    ROCKETMQ,
    /**
     * Redis 传输层
     */
    REDIS,
    /**
     * 本地内存传输层
     */
    LOCAL
}
