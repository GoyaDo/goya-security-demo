package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>RabbitMQ配置注解</p>
 *
 * @author goya
 * @since 2025/6/25 11:36
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface RabbitMqConfig {

    // ===== 核心路由配置 =====
    
    /**
     * 交换器名称
     * 对应Spring RabbitTemplate.convertAndSend(exchange, routingKey, message)
     * 如果为空，将使用默认交换器
     */
    String exchange() default "";

    /**
     * 交换器类型
     * 如果为空，将使用默认交换器类型 topic
     */
    String exchangeType() default "topic";

    /**
     * 路由键
     * 对应Spring RabbitTemplate.convertAndSend(exchange, routingKey, message)
     * 如果为空，将使用topic作为路由键
     */
    String routingKey() default "";

    // ===== 消息属性配置 =====
    
    /**
     * 消息TTL (毫秒)
     * 对应MessageProperties.setExpiration()
     * 0表示不设置TTL
     */
    long messageTtl() default 0;

    /**
     * 消息优先级 (0-255)
     * 对应MessageProperties.setPriority()
     * 0表示不设置优先级
     */
    int priority() default 0;

    /**
     * 关联ID模板
     * 对应MessageProperties.setCorrelationId()
     * 支持SpEL表达式，用于请求-响应模式
     */
    String correlationId() default "";

    /**
     * 回复地址
     * 对应MessageProperties.setReplyTo()
     * 用于请求-响应模式
     */
    String replyTo() default "";

    // ===== 队列配置（监听器端） =====
    
    /**
     * 队列名称
     * 主要用于监听器端的队列创建
     * 如果为空，将使用默认命名规则
     */
    String queueName() default "";

    /**
     * 是否持久化队列
     * 主要用于监听器端的队列配置
     */
    boolean durable() default true;

    // ===== 重试配置 =====
    
    /**
     * 重试次数
     * 用于业务层面的重试控制
     * 0表示使用全局配置
     */
    int retryAttempts() default 0;

    /**
     * 重试间隔 (毫秒)
     * 用于业务层面的重试控制
     * 0表示使用全局配置
     */
    long retryInterval() default 0;

    // ===== 其他配置 =====

    /**
     * 是否持久化交换器
     * 对应ExchangeBuilder.durable()
     * 默认持久化
     */
    boolean durableExchange() default true;

    /**
     * 是否自动删除交换器
     * 对应ExchangeBuilder.autoDelete()
     * 默认不自动删除
     */
    boolean autoDeleteExchange() default false;

    /**
     * 是否持久化队列
     * 对应QueueBuilder.durable()
     * 默认持久化
     */
    boolean durableQueue() default true;

    /**
     * 消息确认模式
     * @return 消息确认模式
     */
    String acknowledgmentMode() default  "auto";

    /**
     * 预取数量
     * @return 预取数量
     */
    int prefetchCount() default 10;

    /**
     * 死信交换器
     * @return 死信交换器
     */
    String deadLetterExchange() default "";
}
