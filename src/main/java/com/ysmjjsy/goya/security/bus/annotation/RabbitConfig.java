package com.ysmjjsy.goya.security.bus.annotation;

import org.springframework.amqp.core.QueueBuilder;

/**
 * <p>Rabbit Mq Config</p>
 *
 * @author goya
 * @since 2025/6/29 16:36
 */
public @interface RabbitConfig {

    /**
     * 是否启用
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 是否持久化(队列)
     */
    boolean queueDurable() default true;

    /**
     * 若没有队列绑定时是否自动删除
     */
    boolean queueAutoDelete() default false;

    /**
     * 是否持久化(交换机)
     */
    boolean exchangeDurable() default true;

    /**
     * 若没有队列绑定时是否自动删除
     */
    boolean exchangeAutoDelete() default false;

    /**
     * 消费者并发数
     */
    int concurrentConsumers() default 1;

    /**
     * 消息过期时间(毫秒)
     */
    int messageTTL() default 0;

    /**
     * 队列在被删除之前可以保持未使用的时间(毫秒)
     */
    int expires() default 0;

    /**
     * 队列的最大长度
     */
    long maxLength() default 0;

    /**
     * 队列的最大长度(字节)
     */
    int maxLengthBytes() default 0;

    /**
     * 设置由于最大消息数或超过最大消息数而丢弃消息时的溢出模式。
     * @see QueueBuilder.Overflow
     */
    String overflow() default "";

    /**
     * 死信交换机
     */
    String dlx() default "";

    /**
     * 死信路由键
     */
    String dlrk() default "";

    /**
     * 最大优先级
     */
    int maxPriority() default 0;

    /**
     * 是否懒加载
     */
    boolean lazy() default false;

    /**
     * 设置主定位器模式，该模式确定队列主定位器将位于节点集群中的哪个节点。
     * @see QueueBuilder.LeaderLocator
     */
    String locator() default "";

    /**
     * 设置‘x-single-active-consumer’队列参数。
     */
    boolean singleActiveConsumer() default false;

    /**
     * 设置queue参数以声明类型为“quorum”而不是“classic”的队列。
     */
    boolean quorum() default false;

    /**
     * 设置queue参数以声明类型为‘stream’而不是‘classic’的队列
     */
    boolean stream() default false;

    /**
     * 设定交付限制；仅适用于仲裁队列。
     */
    int deliveryLimit() default 0;
}
