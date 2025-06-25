package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AcknowledgeMode;

/**
 * <p>RabbitMQ配置模型</p>
 * <p>支持完整的RabbitMQ监听器配置，包括基础设施和消费者配置</p>
 * <p>支持从注解和全局配置中解析配置，专注于监听器注册时的配置需求</p>
 *
 * @author goya
 * @since 2025/6/25 14:00
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RabbitMqConfigModel {

    // ===== 核心路由配置 =====
    
    /**
     * 交换器名称
     */
    private String exchange;

    /**
     * 交换器类型
     * 支持：topic, direct, fanout, headers
     */
    private String exchangeType;

    /**
     * 路由键
     */
    private String routingKey;

    // ===== 交换器配置 =====
    
    /**
     * 是否持久化交换器
     */
    private boolean durableExchange;

    /**
     * 是否自动删除交换器
     */
    private boolean autoDeleteExchange;

    // ===== 队列配置 =====
    
    /**
     * 队列名称
     */
    private String queueName;

    /**
     * 是否持久化队列
     */
    private boolean durableQueue;

    // ===== 消费者配置 =====
    
    /**
     * 消息确认模式
     * 支持：auto, manual, none
     */
    private String acknowledgmentMode;

    /**
     * 预取数量
     */
    private int prefetchCount;

    /**
     * 死信交换器
     */
    private String deadLetterExchange;

    // ===== 消息属性配置（用于事件配置合并） =====
    
    /**
     * 消息TTL (毫秒)
     */
    private long messageTtl;

    /**
     * 消息优先级 (0-255)
     */
    private int priority;

    /**
     * 关联ID
     */
    private String correlationId;

    /**
     * 回复地址
     */
    private String replyTo;

    /**
     * 消息传递模式
     */
    private int deliveryMode;

    // ===== 重试配置 =====
    
    /**
     * 重试次数
     */
    private int retryAttempts;

    /**
     * 重试间隔 (毫秒)
     */
    private long retryInterval;

    // ===== 辅助方法 =====

    /**
     * 获取有效的交换器名称
     * 如果未指定交换器，返回null让RabbitTemplate使用默认交换器
     */
    public String getEffectiveExchange() {
        return StringUtils.isNotBlank(exchange) ? exchange : null;
    }

    /**
     * 获取有效的路由键
     * @param topic 事件主题，作为默认路由键
     * @return 有效的路由键
     */
    public String getEffectiveRoutingKey(String topic) {
        if (StringUtils.isNotBlank(routingKey)) {
            return routingKey;
        }
        return StringUtils.isNotBlank(topic) ? topic : "";
    }

    /**
     * 获取Spring AMQP的确认模式
     */
    public AcknowledgeMode getAcknowledgeMode() {
        if (StringUtils.isBlank(acknowledgmentMode)) {
            return AcknowledgeMode.AUTO;
        }
        
        switch (acknowledgmentMode.toLowerCase()) {
            case "manual":
                return AcknowledgeMode.MANUAL;
            case "none":
                return AcknowledgeMode.NONE;
            case "auto":
            default:
                return AcknowledgeMode.AUTO;
        }
    }

    /**
     * 是否需要设置消息TTL
     */
    public boolean shouldSetTtl() {
        return messageTtl > 0;
    }

    /**
     * 是否需要设置消息优先级
     */
    public boolean shouldSetPriority() {
        return priority > 0;
    }

    /**
     * 是否需要设置关联ID
     */
    public boolean shouldSetCorrelationId() {
        return StringUtils.isNotBlank(correlationId);
    }

    /**
     * 是否需要设置回复地址
     */
    public boolean shouldSetReplyTo() {
        return StringUtils.isNotBlank(replyTo);
    }

    /**
     * 是否需要设置传递模式
     */
    public boolean shouldSetDeliveryMode() {
        return deliveryMode > 0;
    }

    /**
     * 获取完整的队列名称
     * 如果没有指定队列名称，返回null让系统自动生成
     */
    public String getFullQueueName() {
        return StringUtils.isNotBlank(queueName) ? queueName : null;
    }

    /**
     * 是否启用重试机制
     */
    public boolean isRetryEnabled() {
        return retryAttempts > 0;
    }

    /**
     * 获取有效的交换器类型（小写）
     */
    public String getEffectiveExchangeType() {
        return StringUtils.isNotBlank(exchangeType) ? exchangeType.toLowerCase() : "topic";
    }
} 