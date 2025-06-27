package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.enums.MessageModel;
import lombok.Builder;
import lombok.Data;

/**
 * 路由上下文 - 统一不同MQ的路由概念
 * <p>
 * 不同MQ的路由模型映射：
 * - RabbitMQ: Exchange + RoutingKey + Queue + Binding
 * - Kafka: Topic + Partition + ConsumerGroup
 * - RocketMQ: Topic + Tag + ConsumerGroup
 * - Redis: Channel + Pattern
 *
 * @author goya
 * @since 2025/6/26
 */
@Data
@Builder
public class RoutingContext {

    /**
     * 消息模型
     */
    private MessageModel messageModel;

    /**
     * 业务域（用于Exchange/Topic命名）
     */
    private String businessDomain;

    /**
     * 事件类型（用于路由键）
     */
    private String eventType;

    /**
     * 消费者组（用于Queue命名）
     */
    private String consumerGroup;

    /**
     * 路由选择器（支持通配符）
     */
    private String routingSelector;

} 