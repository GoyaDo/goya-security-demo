package com.ysmjjsy.goya.security.bus.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kafka消息配置注解
 * <p>
 * 用于配置Kafka特定的消息传输属性
 *
 * @author goya
 * @since 2025/6/29 16:57
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaConfig {

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 分区数量
     *
     * @return 分区数量
     */
    int partitions() default 3;

    /**
     * 副本因子
     *
     * @return 副本因子
     */
    short replicationFactor() default 1;

    /**
     * 生产者确认模式
     * all: 等待所有副本确认
     * 1: 等待leader确认
     * 0: 不等待确认
     *
     * @return 确认模式
     */
    String acks() default "1";

    /**
     * 批量发送大小（字节）
     *
     * @return 批量大小
     */
    int batchSize() default 16384;

    /**
     * 发送延迟时间（毫秒）
     * 等待更多消息组成批次
     *
     * @return 延迟时间
     */
    long lingerMs() default 5;

    /**
     * 生产者缓冲区大小（字节）
     *
     * @return 缓冲区大小
     */
    long bufferMemory() default 33554432L;

    /**
     * 消费者会话超时时间（毫秒）
     *
     * @return 会话超时时间
     */
    int sessionTimeoutMs() default 30000;

    /**
     * 心跳间隔时间（毫秒）
     *
     * @return 心跳间隔
     */
    int heartbeatIntervalMs() default 3000;

    /**
     * 消费者单次拉取最大记录数
     *
     * @return 最大记录数
     */
    int maxPollRecords() default 500;

    /**
     * 消费者拉取最小字节数
     *
     * @return 最小字节数
     */
    int fetchMinBytes() default 1024;

    /**
     * 消费者拉取最大等待时间（毫秒）
     *
     * @return 最大等待时间
     */
    int fetchMaxWaitMs() default 500;

    /**
     * 是否启用事务
     *
     * @return 是否启用事务
     */
    boolean transactional() default false;

    /**
     * 事务超时时间（毫秒）
     *
     * @return 事务超时时间
     */
    int transactionTimeoutMs() default 60000;

    /**
     * 是否启用幂等性
     *
     * @return 是否启用幂等性
     */
    boolean enableIdempotence() default true;

    /**
     * 最大正在传输的请求数
     *
     * @return 最大请求数
     */
    int maxInFlightRequestsPerConnection() default 5;

    /**
     * 重试次数
     *
     * @return 重试次数
     */
    int retries() default 3;

    /**
     * 重试间隔时间（毫秒）
     *
     * @return 重试间隔
     */
    long retryBackoffMs() default 100;

    /**
     * 消息压缩类型
     * none, gzip, snappy, lz4, zstd
     *
     * @return 压缩类型
     */
    String compressionType() default "none";

    /**
     * 自动偏移重置策略
     * earliest, latest, none
     *
     * @return 重置策略
     */
    String autoOffsetReset() default "earliest";

    /**
     * 是否启用自动提交偏移量
     *
     * @return 是否自动提交
     */
    boolean enableAutoCommit() default false;

    /**
     * 自动提交间隔时间（毫秒）
     *
     * @return 自动提交间隔
     */
    int autoCommitIntervalMs() default 5000;

    /**
     * 主题保留时间（毫秒）
     * -1表示永久保留
     *
     * @return 保留时间
     */
    long retentionMs() default 604800000L; // 7天

    /**
     * 是否启用消息去重
     *
     * @return 是否启用去重
     */
    boolean enableDeduplication() default false;
}
