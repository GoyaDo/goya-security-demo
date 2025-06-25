package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/25 11:34
 */
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class RabbitMqEvent<E extends RabbitMqEvent<E>> extends IEvent<E> {

    private static final long serialVersionUID = 1L;

    /**
     * 队列名称
     * 如果为空，将使用默认命名规则
     */
    @Setter
    protected String queueName = "";

    /**
     * 交换器名称
     * 如果为空，将使用默认交换器
     */
    @Setter
    protected String exchange = "";

    /**
     * 交换器类型
     * 如果为空，将使用默认交换器类型
     */
    @Setter
    protected String exchangeType = "topic";

    /**
     * 路由键
     * 如果为空，将使用 topic 作为路由键
     */
    @Setter
    protected String routingKey = "";

    /**
     * 是否持久化队列
     */
    @Setter
    protected boolean durable = true;

    /**
     * 是否持久化交换器
     */
    @Setter
    protected boolean durableExchange = true;

    /**
     * 是否自动删除队列
     */
    @Setter
    protected boolean autoDelete = false;

    /**
     * 是否自动删除交换器
     */
    @Setter
    protected boolean autoDeleteExchange = false;

    /**
     * 是否排他队列
     */
    @Setter
    protected boolean exclusive = false;

    /**
     * 队列前缀
     */
    @Setter
    protected String queuePrefix = "mall.bus.";

    /**
     * 消息确认模式
     */
    @Setter
    protected String acknowledgmentMode = "manual";

    /**
     * 预取数量
     * 0 表示使用全局配置
     */
    @Setter
    protected int prefetch = 0;

    /**
     * 消息TTL (毫秒)
     * 0 表示使用全局配置，-1 表示不设置TTL
     */
    @Setter
    protected long messageTtl = 0;

    /**
     * 重试次数
     * 0 表示使用全局配置
     */
    @Setter
    protected int retryAttempts = 0;

    /**
     * 重试间隔 (毫秒)
     * 0 表示使用全局配置
     */
    @Setter
    protected long retryInterval = 0;

    /**
     * 死信交换器
     */
    @Setter
    protected String deadLetterExchange = "";
}
