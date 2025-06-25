package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * <p>RabbitMQ事件基类</p>
 * <p>专注于消息发送时的属性配置，不包含基础设施配置</p>
 * <p>基础设施配置（如队列、交换器、消费者配置）应使用@RabbitMqConfig注解</p>
 * <p>支持延迟队列功能，可设置延迟时间实现定时投递</p>
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

    // ===== 延迟队列配置 =====
    
    /**
     * 延迟时间（毫秒）
     * 0表示不延迟，立即发送
     * >0表示延迟指定毫秒后投递到目标队列
     * 
     * 实现原理：
     * 1. 消息先发送到延迟队列（设置TTL=delayMillis）
     * 2. 延迟队列无消费者，消息TTL过期后进入死信交换器
     * 3. 死信交换器将消息路由到最终的目标队列
     * 4. 目标队列的消费者处理消息
     */
    @Setter
    protected long delayMillis = 0;

    /**
     * 延迟投递的目标交换器
     * 仅在延迟模式下使用，指定消息延迟后要投递到的目标交换器
     * 如果不设置，将使用exchange字段作为目标交换器
     */
    @Setter
    protected String delayTargetExchange = "";

    /**
     * 延迟投递的目标路由键
     * 仅在延迟模式下使用，指定消息延迟后要使用的路由键
     * 如果不设置，将使用routingKey字段作为目标路由键
     */
    @Setter
    protected String delayTargetRoutingKey = "";

    // ===== 延迟队列辅助方法 =====

    /**
     * 是否启用延迟投递
     * @return true: 启用延迟, false: 立即投递
     */
    public boolean isDelayEnabled() {
        return delayMillis > 0;
    }

    /**
     * 获取延迟秒数（向上取整）
     * 用于延迟队列分组，减少队列数量
     * 例如：1500ms -> 2s, 2000ms -> 2s
     * @return 延迟秒数
     */
    public int getDelaySeconds() {
        if (delayMillis <= 0) {
            return 0;
        }
        return (int) Math.ceil(delayMillis / 1000.0);
    }

    /**
     * 获取有效的延迟目标交换器
     * @return 延迟目标交换器名称
     */
    public String getEffectiveDelayTargetExchange() {
        if (org.apache.commons.lang3.StringUtils.isNotBlank(delayTargetExchange)) {
            return delayTargetExchange;
        }
        return exchange;
    }

    /**
     * 获取有效的延迟目标路由键
     * @param defaultTopic 默认主题（通常是事件的topic）
     * @return 延迟目标路由键
     */
    public String getEffectiveDelayTargetRoutingKey(String defaultTopic) {
        if (org.apache.commons.lang3.StringUtils.isNotBlank(delayTargetRoutingKey)) {
            return delayTargetRoutingKey;
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(routingKey)) {
            return routingKey;
        }
        return org.apache.commons.lang3.StringUtils.isNotBlank(defaultTopic) ? defaultTopic : "";
    }

    /**
     * 获取延迟队列名称
     * @param prefix 队列前缀
     * @return 延迟队列名称
     */
    public String getDelayQueueName(String prefix) {
        if (!isDelayEnabled()) {
            return "";
        }
        return prefix + ".delay." + getDelaySeconds() + "s.queue";
    }
}
