package com.ysmjjsy.goya.security.bus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis消息配置注解
 * <p>
 * 用于配置Redis特定的消息传输属性
 *
 * @author goya
 * @since 2025/6/29 21:43
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisConfig {

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 消息生存时间（秒）
     * -1表示永不过期
     *
     * @return TTL时间
     */
    long ttl() default -1;

    /**
     * 队列最大长度
     * -1表示无限制
     *
     * @return 最大长度
     */
    long maxQueueLength() default -1;

    /**
     * 是否启用消息持久化
     *
     * @return 是否持久化
     */
    boolean persistent() default true;

    /**
     * 消费者并发数
     *
     * @return 并发数
     */
    int concurrency() default 1;

    /**
     * 消息重试次数
     *
     * @return 重试次数
     */
    int maxRetries() default 3;

    /**
     * 重试间隔时间（毫秒）
     *
     * @return 重试间隔
     */
    long retryInterval() default 1000;

    /**
     * 批量处理大小
     *
     * @return 批量大小
     */
    int batchSize() default 100;

    /**
     * 消费超时时间（秒）
     *
     * @return 超时时间
     */
    long consumeTimeout() default 30;

    /**
     * 是否启用消息去重
     *
     * @return 是否启用去重
     */
    boolean enableDeduplication() default true;

    /**
     * 去重记录过期时间（小时）
     *
     * @return 过期时间
     */
    int deduplicationExpireHours() default 24;

    /**
     * 是否启用死信队列
     *
     * @return 是否启用死信队列
     */
    boolean enableDeadLetterQueue() default false;

    /**
     * 死信队列名称后缀
     *
     * @return 死信队列后缀
     */
    String deadLetterQueueSuffix() default ".dlq";

    /**
     * 最大消息大小（字节）
     * -1表示无限制
     *
     * @return 最大消息大小
     */
    long maxMessageSize() default -1;

    /**
     * 连接池最大连接数
     *
     * @return 最大连接数
     */
    int maxConnections() default 10;

    /**
     * 连接池最大空闲连接数
     *
     * @return 最大空闲连接数
     */
    int maxIdle() default 5;

    /**
     * 连接池最小空闲连接数
     *
     * @return 最小空闲连接数
     */
    int minIdle() default 1;

    /**
     * 连接超时时间（毫秒）
     *
     * @return 连接超时时间
     */
    int connectionTimeout() default 5000;

    /**
     * 读取超时时间（毫秒）
     *
     * @return 读取超时时间
     */
    int readTimeout() default 3000;
}
