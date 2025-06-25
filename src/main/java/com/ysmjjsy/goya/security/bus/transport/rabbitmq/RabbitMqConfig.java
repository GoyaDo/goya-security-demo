package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/25 11:36
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface RabbitMqConfig {

    /**
     * 队列名称
     * 如果为空，将使用默认命名规则
     */
    String queueName() default "";

    /**
     * 交换器名称
     * 如果为空，将使用默认交换器
     */
    String exchange() default "";

    /**
     * 路由键
     * 如果为空，将使用 topic 作为路由键
     */
    String routingKey() default "";

    /**
     * 是否持久化队列
     */
    boolean durable() default true;

    /**
     * 是否自动删除队列
     */
    boolean autoDelete() default false;

    /**
     * 是否排他队列
     */
    boolean exclusive() default false;

    /**
     * 预取数量
     * 0 表示使用全局配置
     */
    int prefetch() default 0;

    /**
     * 消息TTL (毫秒)
     * 0 表示使用全局配置，-1 表示不设置TTL
     */
    long messageTtl() default 0;

    /**
     * 重试次数
     * 0 表示使用全局配置
     */
    int retryAttempts() default 0;

    /**
     * 重试间隔 (毫秒)
     * 0 表示使用全局配置
     */
    long retryInterval() default 0;
}
