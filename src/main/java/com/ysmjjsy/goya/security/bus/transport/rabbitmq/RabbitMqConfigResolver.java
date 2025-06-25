package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * <p>RabbitMQ配置解析器</p>
 * <p>支持完整的配置解析，包括acknowledgmentMode和exchangeType</p>
 * <p>配置优先级：RabbitMqConfig注解 > RabbitMqEvent事件 > BusProperties全局配置</p>
 * <p>专注于监听器注册时的配置需求</p>
 *
 * @author goya
 * @since 2025/6/25 14:10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqConfigResolver {

    private final BusProperties busProperties;

    /**
     * 从注解解析配置（监听器注册时使用）
     */
    public RabbitMqConfigModel resolveFromAnnotation(RabbitMqConfig annotation, String topic) {
        if (annotation == null) {
            return resolveFromDefaults(topic);
        }

        BusProperties.RabbitMQ globalConfig = busProperties.getRabbitmq();

        RabbitMqConfigModel config = RabbitMqConfigModel.builder()
                // 核心路由配置
                .exchange(getValueOrDefault(annotation.exchange(), globalConfig.getDefaultExchangeName()))
                .exchangeType(getValueOrDefault(annotation.exchangeType(), globalConfig.getExchangeType()))
                .routingKey(getValueOrDefault(annotation.routingKey(), topic))
                
                // 交换器配置
                .durableExchange(annotation.durableExchange())
                .autoDeleteExchange(annotation.autoDeleteExchange())
                
                // 队列配置
                .queueName(annotation.queueName())
                .durableQueue(annotation.durableQueue())
                
                // 消费者配置
                .acknowledgmentMode(getValueOrDefault(annotation.acknowledgmentMode(), globalConfig.getAcknowledgmentMode()))
                .prefetchCount(annotation.prefetchCount() > 0 ? annotation.prefetchCount() : globalConfig.getPrefetchCount())
                .deadLetterExchange(getValueOrDefault(annotation.deadLetterExchange(), globalConfig.getDeadLetterExchange()))
                
                // 消息属性配置（从注解获取，通常为默认值）
                .messageTtl(annotation.messageTtl() > 0 ? annotation.messageTtl() : 
                           (globalConfig.getMessageTtl() != null ? globalConfig.getMessageTtl() : 0))
                .priority(annotation.priority())
                .correlationId(annotation.correlationId())
                .replyTo(annotation.replyTo())
                .deliveryMode(0) // 注解中不设置传递模式
                
                // 重试配置
                .retryAttempts(annotation.retryAttempts() > 0 ? annotation.retryAttempts() : globalConfig.getRetryAttempts())
                .retryInterval(annotation.retryInterval() > 0 ? annotation.retryInterval() : globalConfig.getRetryInterval())
                .build();

        log.debug("Resolved RabbitMQ config from annotation for topic '{}': exchange={}, exchangeType={}, ackMode={}", 
                topic, config.getExchange(), config.getExchangeType(), config.getAcknowledgmentMode());
        return config;
    }

    /**
     * 从RabbitMqEvent解析配置（消息发送时使用）
     */
    public RabbitMqConfigModel resolveFromEvent(RabbitMqEvent<?> event) {
        if (event == null) {
            return resolveFromDefaults(null);
        }

        BusProperties.RabbitMQ globalConfig = busProperties.getRabbitmq();

        RabbitMqConfigModel config = RabbitMqConfigModel.builder()
                // 核心路由配置
                .exchange(event.getExchange())
                .exchangeType(globalConfig.getExchangeType()) // 事件不指定交换器类型
                .routingKey(event.getRoutingKey())
                
                // 交换器配置（使用全局默认）
                .durableExchange(globalConfig.isDurableExchange())
                .autoDeleteExchange(globalConfig.isAutoDeleteExchange())
                
                // 队列配置（事件发送时不需要）
                .queueName("")
                .durableQueue(globalConfig.isDurableQueue())
                
                // 消费者配置（事件发送时不需要）
                .acknowledgmentMode(globalConfig.getAcknowledgmentMode())
                .prefetchCount(globalConfig.getPrefetchCount())
                .deadLetterExchange(globalConfig.getDeadLetterExchange())
                
                // 消息属性配置（从事件获取）
                .messageTtl(event.getMessageTtl())
                .priority(event.getPriority())
                .correlationId(event.getCorrelationId())
                .replyTo(event.getReplyTo())
                .deliveryMode(event.getDeliveryMode())
                
                // 重试配置（使用全局配置，事件不包含重试配置）
                .retryAttempts(globalConfig.getRetryAttempts())
                .retryInterval(globalConfig.getRetryInterval())
                .build();

        log.debug("Resolved RabbitMQ config from event '{}': exchange={}, messageTtl={}, priority={}", 
                event.getEventType(), config.getExchange(), config.getMessageTtl(), config.getPriority());
        return config;
    }

    /**
     * 合并注解配置和事件配置
     * 注解配置优先级高于事件配置
     */
    public RabbitMqConfigModel mergeConfigs(RabbitMqConfig annotation, RabbitMqEvent<?> event, String topic) {
        if (annotation == null && event == null) {
            return resolveFromDefaults(topic);
        }

        if (annotation == null) {
            return resolveFromEvent(event);
        }

        if (event == null) {
            return resolveFromAnnotation(annotation, topic);
        }

        // 合并配置：注解优先，但消息属性从事件获取
        RabbitMqConfigModel annotationConfig = resolveFromAnnotation(annotation, topic);
        RabbitMqConfigModel eventConfig = resolveFromEvent(event);

        RabbitMqConfigModel mergedConfig = RabbitMqConfigModel.builder()
                // 核心路由配置（注解优先）
                .exchange(getValueOrDefault(annotationConfig.getExchange(), eventConfig.getExchange()))
                .exchangeType(annotationConfig.getExchangeType()) // 注解指定交换器类型
                .routingKey(getValueOrDefault(annotationConfig.getRoutingKey(), eventConfig.getRoutingKey()))
                
                // 交换器配置（注解优先）
                .durableExchange(annotationConfig.isDurableExchange())
                .autoDeleteExchange(annotationConfig.isAutoDeleteExchange())
                
                // 队列配置（注解优先）
                .queueName(annotationConfig.getQueueName())
                .durableQueue(annotationConfig.isDurableQueue())
                
                // 消费者配置（注解优先）
                .acknowledgmentMode(annotationConfig.getAcknowledgmentMode())
                .prefetchCount(annotationConfig.getPrefetchCount())
                .deadLetterExchange(annotationConfig.getDeadLetterExchange())
                
                // 消息属性配置（事件优先，因为这些是消息级别的配置）
                .messageTtl(eventConfig.getMessageTtl() > 0 ? eventConfig.getMessageTtl() : annotationConfig.getMessageTtl())
                .priority(eventConfig.getPriority() > 0 ? eventConfig.getPriority() : annotationConfig.getPriority())
                .correlationId(getValueOrDefault(eventConfig.getCorrelationId(), annotationConfig.getCorrelationId()))
                .replyTo(getValueOrDefault(eventConfig.getReplyTo(), annotationConfig.getReplyTo()))
                .deliveryMode(eventConfig.getDeliveryMode() > 0 ? eventConfig.getDeliveryMode() : annotationConfig.getDeliveryMode())
                
                // 重试配置（注解优先）
                .retryAttempts(annotationConfig.getRetryAttempts() > 0 ? annotationConfig.getRetryAttempts() : eventConfig.getRetryAttempts())
                .retryInterval(annotationConfig.getRetryInterval() > 0 ? annotationConfig.getRetryInterval() : eventConfig.getRetryInterval())
                .build();

        log.debug("Merged RabbitMQ config for topic '{}': exchange={}, exchangeType={}, ackMode={}", 
                topic, mergedConfig.getExchange(), mergedConfig.getExchangeType(), mergedConfig.getAcknowledgmentMode());
        return mergedConfig;
    }

    /**
     * 从全局默认配置解析
     */
    public RabbitMqConfigModel resolveFromDefaults(String topic) {
        BusProperties.RabbitMQ globalConfig = busProperties.getRabbitmq();

        return RabbitMqConfigModel.builder()
                // 核心路由配置
                .exchange(globalConfig.getDefaultExchangeName())
                .exchangeType(globalConfig.getExchangeType())
                .routingKey(topic)
                
                // 交换器配置
                .durableExchange(globalConfig.isDurableExchange())
                .autoDeleteExchange(globalConfig.isAutoDeleteExchange())
                
                // 队列配置
                .queueName(null) // 队列名称由业务逻辑生成
                .durableQueue(globalConfig.isDurableQueue())
                
                // 消费者配置
                .acknowledgmentMode(globalConfig.getAcknowledgmentMode())
                .prefetchCount(globalConfig.getPrefetchCount())
                .deadLetterExchange(globalConfig.getDeadLetterExchange())
                
                // 消息属性配置
                .messageTtl(globalConfig.getMessageTtl() != null ? globalConfig.getMessageTtl() : 0)
                .priority(0) // 默认不设置优先级
                .correlationId("")
                .replyTo("")
                .deliveryMode(0) // 默认使用RabbitMQ默认传递模式
                
                // 重试配置
                .retryAttempts(globalConfig.getRetryAttempts())
                .retryInterval(globalConfig.getRetryInterval())
                .build();
    }

    /**
     * 获取值或默认值（字符串）
     */
    private String getValueOrDefault(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }
} 