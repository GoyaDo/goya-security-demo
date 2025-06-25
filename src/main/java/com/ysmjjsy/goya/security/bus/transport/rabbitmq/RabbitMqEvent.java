package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * <p>RabbitMQ事件基类</p>
 * <p>专注于消息发送时的属性配置，不包含基础设施配置</p>
 * <p>基础设施配置（如队列、交换器、消费者配置）应使用@RabbitMqConfig注解</p>
 *
 * @author goya
 * @since 2025/6/25 11:34
 */
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class RabbitMqEvent<E extends RabbitMqEvent<E>> extends IEvent<E> {

    private static final long serialVersionUID = 1L;

    // ===== 核心路由配置 =====
    
    /**
     * 交换器名称
     * 对应Spring RabbitTemplate.convertAndSend(exchange, routingKey, message)
     * 如果为空，将使用默认交换器
     */
    @Setter
    protected String exchange = "";

    /**
     * 路由键
     * 对应Spring RabbitTemplate.convertAndSend(exchange, routingKey, message)
     * 如果为空，将使用topic作为路由键
     */
    @Setter
    protected String routingKey = "";

    // ===== 消息属性配置 =====
    
    /**
     * 消息TTL (毫秒)
     * 对应MessageProperties.setExpiration()
     * 0表示不设置TTL
     */
    @Setter
    protected long messageTtl = 0;

    /**
     * 消息优先级 (0-255)
     * 对应MessageProperties.setPriority()
     * 0表示不设置优先级
     */
    @Setter
    protected int priority = 0;

    /**
     * 关联ID
     * 对应MessageProperties.setCorrelationId()
     * 用于请求-响应模式
     */
    @Setter
    protected String correlationId = "";

    /**
     * 回复地址
     * 对应MessageProperties.setReplyTo()
     * 用于请求-响应模式
     */
    @Setter
    protected String replyTo = "";

    /**
     * 消息传递模式
     * 对应MessageProperties.setDeliveryMode()
     * 1=NON_PERSISTENT, 2=PERSISTENT
     * 0表示使用默认值（持久化）
     */
    @Setter
    protected int deliveryMode = 0;
}
