package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import com.ysmjjsy.goya.security.bus.transport.TransportResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ 事件传输实现
 * 使用 RabbitMQ Topic Exchange 实现事件传输
 * 
 * 功能特性：
 * - Topic Exchange 支持灵活的消息路由
 * - 支持消息持久化和可靠传输
 * - 支持消息确认机制
 * - 支持死信队列和重试机制
 * - 动态队列和绑定管理
 * - 支持事务和消息顺序
 * - 支持消息TTL和延迟重试
 * - 条件化启用RabbitMQ传输
 *
 * @author goya
 * @since 2025/1/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMQEventTransport implements EventTransport {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ConnectionFactory connectionFactory;
    private final EventSerializer eventSerializer;
    private final BusProperties eventBusProperties;

    private final RabbitMQManagementTool managementTool;

    private final Map<String, TopicExchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, Map<IEventListener<?>, RabbitMQEventMessageListener<?>>> subscriptions = new ConcurrentHashMap<>();

    // 死信队列相关
    private TopicExchange deadLetterExchange;
    private Queue retryQueue;
    private final Map<String, Integer> retryCounters = new ConcurrentHashMap<>();

    private volatile boolean started = false;

    @PostConstruct
    public void init() {
        if (!eventBusProperties.getRabbitmq().isEnabled()) {
            log.info("RabbitMQ event transport is disabled");
            return;
        }

        // 配置RabbitTemplate
        configureRabbitTemplate();
        
        // 创建默认交换器
        createDefaultExchange();
        
        // 创建死信队列相关资源
        createDeadLetterResources();
        
        log.info("RabbitMQ event transport initialized with exchange: {}", 
                eventBusProperties.getRabbitmq().getDefaultExchangeName());
    }

    @Override
    public BusRemoteType getTransportType() {
        return BusRemoteType.RABBITMQ;
    }

    @Override
    public CompletableFuture<TransportResult> send(IEvent<?> event) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!check(event)) {
                    return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(),
                            new EventHandleException(event.getEventId(), "Event is local event"));
                }

                if (!eventBusProperties.getRabbitmq().isEnabled()) {
                    return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(), 
                            new IllegalStateException("RabbitMQ transport is disabled"));
                }

                // 确保交换器存在
                TopicExchange exchange = getOrCreateExchange(event.getTopic());
                
                // 序列化事件
                String serializedEvent = eventSerializer.serialize(event);
                
                // 发送消息，使用topic作为routing key
                rabbitTemplate.convertAndSend(exchange.getName(), event.getTopic(), serializedEvent, message -> {
                    // 设置消息属性
                    MessageProperties properties = message.getMessageProperties();
                    properties.setContentType("application/json");
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    properties.setMessageId(event.getEventId());
                    properties.setTimestamp(new Date());

                    // 设置TTL
                    if (eventBusProperties.getRabbitmq().getMessageTtl() != null) {
                        properties.setExpiration(String.valueOf(eventBusProperties.getRabbitmq().getMessageTtl()));
                    }
                    
                    // 设置重试相关头部
                    properties.setHeader("retry-count", 0);
                    properties.setHeader("max-retry-attempts", eventBusProperties.getRabbitmq().getRetryAttempts());
                    properties.setHeader("original-topic", event.getTopic());
                    
                    return message;
                });

                log.debug("Event sent to RabbitMQ exchange '{}' with topic '{}': {}", 
                        exchange.getName(), event.getTopic(), event.getEventId());
                
                return TransportResult.success(getTransportType(), event.getTopic(), event.getEventId());

            } catch (Exception e) {
                log.error("Failed to send event {} to RabbitMQ topic '{}'", event.getEventId(), event.getTopic(), e);
                return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(), e);
            }
        });
    }

    @Override
    public <E extends IEvent<E>> void subscribe(String topic, IEventListener<E> listener, Class<E> eventType) {
        if (!started) {
            throw new IllegalStateException("RabbitMQ transport not started");
        }

        if (!eventBusProperties.getRabbitmq().isEnabled()) {
            log.warn("RabbitMQ transport is disabled, subscription ignored for topic: {}", topic);
            return;
        }

        try {
            // 确保交换器存在
            TopicExchange exchange = getOrCreateExchange(topic);
            
            // 创建队列
            Queue queue = createQueueForSubscription(topic, listener);
            
            // 创建绑定
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(topic);
            rabbitAdmin.declareBinding(binding);
            
            // 创建消息监听器
            RabbitMQEventMessageListener<E> messageListener =
                    new RabbitMQEventMessageListener<>(listener, eventType, eventSerializer, topic, this);
            
            // 创建消息监听容器
            SimpleMessageListenerContainer container = createMessageListenerContainer(queue, messageListener);
            
            // 保存订阅信息
            subscriptions.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                    .put(listener, messageListener);
            containers.put(getContainerKey(topic, listener), container);
            
            // 启动容器
            container.start();
            
            log.info("Subscribed to RabbitMQ topic '{}' for event type {} with queue '{}'", 
                    topic, eventType.getSimpleName(), queue.getName());

        } catch (Exception e) {
            log.error("Failed to subscribe to RabbitMQ topic '{}'", topic, e);
            throw new RuntimeException("Failed to subscribe to topic: " + topic, e);
        }
    }

    @Override
    public void unsubscribe(String topic, IEventListener<?> listener) {
        Map<IEventListener<?>, RabbitMQEventMessageListener<?>> topicSubscriptions = subscriptions.get(topic);
        if (topicSubscriptions != null) {
            RabbitMQEventMessageListener<?> messageListener = topicSubscriptions.remove(listener);
            if (messageListener != null) {
                // 停止消息监听容器
                String containerKey = getContainerKey(topic, listener);
                SimpleMessageListenerContainer container = containers.remove(containerKey);
                if (container != null) {
                    container.stop();
                }
                
                log.info("Unsubscribed from RabbitMQ topic '{}'", topic);
            }
        }
    }

    @Override
    public void start() {
        if (!started && eventBusProperties.getRabbitmq().isEnabled()) {
            started = true;
            log.info("RabbitMQ event transport started");
        }
    }

    @Override
    public void stop() {
        if (started) {
            // 停止所有消息监听容器
            containers.values().forEach(SimpleMessageListenerContainer::stop);
            containers.clear();
            subscriptions.clear();
            retryCounters.clear();
            
            started = false;
            log.info("RabbitMQ event transport stopped");
        }
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    @Override
    public boolean supportsTransaction() {
        // RabbitMQ 支持事务
        return true;
    }

    @Override
    public boolean supportsOrdering() {
        // RabbitMQ 支持消息顺序（在单个队列内）
        return true;
    }

    /**
     * 处理消息重试
     * 
     * @param message 原始消息
     * @param topic 主题
     * @param exception 处理异常
     */
    public void handleRetry(Message message, String topic, Exception exception) {
        MessageProperties properties = message.getMessageProperties();
        Integer retryCount = (Integer) properties.getHeaders().getOrDefault("retry-count", 0);
        Integer maxRetryAttempts = (Integer) properties.getHeaders().getOrDefault("max-retry-attempts", 
                eventBusProperties.getRabbitmq().getRetryAttempts());

        if (retryCount < maxRetryAttempts) {
            // 增加重试次数
            retryCount++;
            properties.setHeader("retry-count", retryCount);
            
            // 计算延迟时间
            long delay = eventBusProperties.getRabbitmq().getRetryInterval() * retryCount;
            
            log.warn("Retrying message processing (attempt {}/{}) for topic '{}' after {}ms delay: {}", 
                    retryCount, maxRetryAttempts, topic, delay, exception.getMessage());
            
            // 延迟重试
            scheduleRetry(message, topic, delay);
            
        } else {
            log.error("Max retry attempts ({}) exceeded for topic '{}', sending to dead letter exchange: {}", 
                    maxRetryAttempts, topic, exception.getMessage());
            
            // 发送到死信队列
            sendToDeadLetterQueue(message, topic, exception);
        }
    }

    /**
     * 配置RabbitTemplate
     */
    private void configureRabbitTemplate() {
        // 启用确认模式
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("Message not delivered: {}", cause);
            }
        });
        
        // 启用返回回调
        rabbitTemplate.setReturnsCallback(returned -> log.warn("Message returned: {}, replyCode: {}, replyText: {}",
                returned.getMessage(), returned.getReplyCode(), returned.getReplyText()));
        
        // 设置强制模式
        rabbitTemplate.setMandatory(true);
    }

    /**
     * 创建默认交换器
     */
    private void createDefaultExchange() {
        BusProperties.RabbitMQ config = eventBusProperties.getRabbitmq();
        TopicExchange exchange = new TopicExchange(
                config.getDefaultExchangeName(),
                config.isDurableExchange(),
                config.isAutoDeleteExchange()
        );
        
        rabbitAdmin.declareExchange(exchange);
        exchanges.put(config.getDefaultExchangeName(), exchange);
    }

    /**
     * 创建死信队列相关资源
     */
    private void createDeadLetterResources() {
        BusProperties.RabbitMQ config = eventBusProperties.getRabbitmq();
        
        if (config.getDeadLetterExchange() != null && !config.getDeadLetterExchange().isEmpty()) {
            // 创建死信交换器
            deadLetterExchange = new TopicExchange(
                    config.getDeadLetterExchange(),
                    config.isDurableExchange(),
                    config.isAutoDeleteExchange()
            );
            rabbitAdmin.declareExchange(deadLetterExchange);
            
            // 创建死信队列
            Queue deadLetterQueue = QueueBuilder
                    .durable(config.getQueuePrefix() + "dead-letter")
                    .build();
            rabbitAdmin.declareQueue(deadLetterQueue);
            
            // 绑定死信队列到死信交换器
            Binding deadLetterBinding = BindingBuilder
                    .bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with("dead-letter.#");
            rabbitAdmin.declareBinding(deadLetterBinding);
            
            log.info("Dead letter exchange '{}' and queue created", config.getDeadLetterExchange());
        }
        
        // 创建重试队列
        createRetryQueue();
    }

    /**
     * 创建重试队列
     */
    private void createRetryQueue() {
        BusProperties.RabbitMQ config = eventBusProperties.getRabbitmq();
        
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", config.getRetryInterval());
        args.put("x-dead-letter-exchange", config.getDefaultExchangeName());
        
        retryQueue = QueueBuilder
                .durable(config.getQueuePrefix() + "retry")
                .withArguments(args)
                .build();
        
        rabbitAdmin.declareQueue(retryQueue);
        
        log.debug("Retry queue '{}' created with TTL: {}ms", retryQueue.getName(), config.getRetryInterval());
    }

    /**
     * 获取或创建交换器
     */
    private TopicExchange getOrCreateExchange(String topic) {
        String exchangeName = eventBusProperties.getRabbitmq().getDefaultExchangeName();
        return exchanges.computeIfAbsent(exchangeName, name -> {
            BusProperties.RabbitMQ config = eventBusProperties.getRabbitmq();
            TopicExchange exchange = new TopicExchange(name, config.isDurableExchange(), config.isAutoDeleteExchange());
            rabbitAdmin.declareExchange(exchange);
            return exchange;
        });
    }

    /**
     * 为订阅创建队列
     */
    private Queue createQueueForSubscription(String topic, IEventListener<?> listener) {
        String queueName = eventBusProperties.getRabbitmq().getQueuePrefix() + topic + "." + 
                listener.getClass().getSimpleName();
        
        return queues.computeIfAbsent(queueName, name -> {
            BusProperties.RabbitMQ config = eventBusProperties.getRabbitmq();
            
            try {
                // 首先尝试被动声明队列，检查队列是否已存在
                Queue existingQueue = tryDeclareExistingQueue(name);
                if (existingQueue != null) {
                    log.info("Using existing queue: {}", name);
                    return existingQueue;
                }
                
                // 队列不存在，创建新队列
                return createNewQueueWithFullConfig(name, config, topic);
                
            } catch (Exception e) {
                log.warn("Failed to create queue '{}' with full config: {}", name, e.getMessage());
                return handleQueueConflictWithManagement(name, config, topic, e);
            }
        });
    }

    /**
     * 使用管理工具处理队列冲突
     */
    private Queue handleQueueConflictWithManagement(String queueName, BusProperties.RabbitMQ config, String topic, Exception originalException) {
        try {
            // 检查是否启用自动清理
            if (!config.isAutoCleanupConflicts()) {
                log.warn("Auto cleanup is disabled, falling back to simple queue creation for '{}'", queueName);
                return createQueueWithoutTtl(queueName, config, topic);
            }

            log.info("Attempting to resolve queue conflict for '{}' using management tool", queueName);
            
            // 检查队列是否真的存在
            if (managementTool.queueExists(queueName)) {
                log.info("Queue '{}' exists with incompatible configuration, attempting cleanup", queueName);
                
                // 尝试清理队列
                boolean cleaned = managementTool.cleanupQueue(queueName);
                if (cleaned) {
                    log.info("Successfully cleaned up queue '{}', recreating with new configuration", queueName);
                    
                    // 重新创建队列
                    return createNewQueueWithFullConfig(queueName, config, topic);
                } else {
                    log.warn("Failed to cleanup queue '{}', falling back to simple queue creation", queueName);
                    return createQueueWithoutTtl(queueName, config, topic);
                }
            } else {
                log.debug("Queue '{}' does not exist, creating new one", queueName);
                return createNewQueueWithFullConfig(queueName, config, topic);
            }
            
        } catch (Exception e) {
            log.error("Management tool failed to resolve queue conflict for '{}': {}", queueName, e.getMessage());
            // 最后的fallback
            return createQueueWithoutTtl(queueName, config, topic);
        }
    }

    /**
     * 创建新队列（完整配置）
     */
    private Queue createNewQueueWithFullConfig(String queueName, BusProperties.RabbitMQ config, String topic) {
        QueueBuilder builder = QueueBuilder.durable(queueName);
        
        if (config.isAutoDeleteQueue()) {
            builder.autoDelete();
        }
        if (config.isExclusiveQueue()) {
            builder.exclusive();
        }
        
        // 设置死信交换器
        if (config.getDeadLetterExchange() != null && !config.getDeadLetterExchange().isEmpty()) {
            builder.withArgument("x-dead-letter-exchange", config.getDeadLetterExchange());
            builder.withArgument("x-dead-letter-routing-key", "dead-letter." + topic);
        }
        
        // 设置消息TTL
        if (config.getMessageTtl() != null) {
            builder.withArgument("x-message-ttl", config.getMessageTtl());
        }
        
        Queue queue = builder.build();
        
        rabbitAdmin.declareQueue(queue);
        log.info("Created new queue: {} with TTL: {}", queueName, config.getMessageTtl());
        return queue;
    }

    /**
     * 尝试被动声明已存在的队列
     */
    private Queue tryDeclareExistingQueue(String queueName) {
        try {
            // 使用被动声明检查队列是否存在
            rabbitAdmin.getQueueProperties(queueName);
            
            // 如果没有抛出异常，说明队列存在，创建一个简单的Queue对象
            return QueueBuilder.durable(queueName).build();
            
        } catch (Exception e) {
            // 队列不存在或其他错误
            log.debug("Queue '{}' does not exist or cannot be accessed: {}", queueName, e.getMessage());
            return null;
        }
    }

    /**
     * 创建不带TTL的队列（用于兼容已存在的队列）
     */
    private Queue createQueueWithoutTtl(String queueName, BusProperties.RabbitMQ config, String topic) {
        try {
            QueueBuilder builder = QueueBuilder.durable(queueName);
            
            if (config.isAutoDeleteQueue()) {
                builder.autoDelete();
            }
            if (config.isExclusiveQueue()) {
                builder.exclusive();
            }
            
            // 只设置死信交换器（如果配置了的话）
            if (config.getDeadLetterExchange() != null && !config.getDeadLetterExchange().isEmpty()) {
                builder.withArgument("x-dead-letter-exchange", config.getDeadLetterExchange());
                builder.withArgument("x-dead-letter-routing-key", "dead-letter." + topic);
            }
            
            Queue queue = builder.build();
            
            rabbitAdmin.declareQueue(queue);
            log.info("Created queue without TTL: {}", queueName);
            return queue;
            
        } catch (Exception e) {
            log.error("Failed to create queue '{}' even without TTL: {}", queueName, e.getMessage());
            
            // 最后尝试：创建最简单的持久化队列
            Queue simpleQueue = QueueBuilder.durable(queueName).build();
            try {
                rabbitAdmin.declareQueue(simpleQueue);
                log.info("Created simple durable queue: {}", queueName);
                return simpleQueue;
            } catch (Exception ex) {
                log.error("Failed to create simple queue '{}': {}", queueName, ex.getMessage());
                throw new RuntimeException("Unable to create queue: " + queueName, ex);
            }
        }
    }

    /**
     * 创建消息监听容器
     */
    private SimpleMessageListenerContainer createMessageListenerContainer(Queue queue, 
                                                                         RabbitMQEventMessageListener<?> messageListener) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(messageListener);
        
        // 设置预取数量
        container.setPrefetchCount(eventBusProperties.getRabbitmq().getPrefetchCount());
        
        // 设置确认模式
        if ("manual".equals(eventBusProperties.getRabbitmq().getAcknowledgmentMode())) {
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        } else {
            container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        }
        
        // 设置并发消费者数量
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(3);
        
        return container;
    }

    /**
     * 安排重试
     */
    private void scheduleRetry(Message message, String topic, long delay) {
        // 发送到重试队列，利用TTL实现延迟
        rabbitTemplate.convertAndSend(retryQueue.getName(), message, msg -> {
            msg.getMessageProperties().setHeader("original-routing-key", topic);
            msg.getMessageProperties().setHeader("original-exchange", 
                    eventBusProperties.getRabbitmq().getDefaultExchangeName());
            return msg;
        });
    }

    /**
     * 发送到死信队列
     */
    private void sendToDeadLetterQueue(Message message, String topic, Exception exception) {
        if (deadLetterExchange != null) {
            String routingKey = "dead-letter." + topic;
            
            rabbitTemplate.convertAndSend(deadLetterExchange.getName(), routingKey, message, msg -> {
                msg.getMessageProperties().setHeader("failure-reason", exception.getMessage());
                msg.getMessageProperties().setHeader("failure-timestamp", System.currentTimeMillis());
                msg.getMessageProperties().setHeader("original-topic", topic);
                return msg;
            });
            
            log.warn("Message sent to dead letter exchange '{}' with routing key '{}'", 
                    deadLetterExchange.getName(), routingKey);
        } else {
            log.error("Dead letter exchange not configured, message will be discarded for topic: {}", topic);
        }
    }

    /**
     * 获取重试队列名称
     */
    public String getRetryQueueName() {
        return retryQueue != null ? retryQueue.getName() : null;
    }

    /**
     * 生成容器键
     */
    private String getContainerKey(String topic, IEventListener<?> listener) {
        return topic + ":" + listener.getClass().getName() + "@" + System.identityHashCode(listener);
    }
} 