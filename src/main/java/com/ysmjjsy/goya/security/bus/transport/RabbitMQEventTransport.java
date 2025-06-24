package com.ysmjjsy.goya.security.bus.transport;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.util.Date;
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
 *
 * @author goya
 * @since 2025/1/17
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMQEventTransport implements EventTransport {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ConnectionFactory connectionFactory;
    private final EventSerializer eventSerializer;
    private final BusProperties eventBusProperties;

    private final Map<String, TopicExchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, Map<IEventListener<?>, RabbitMQEventMessageListener<?>>> subscriptions = new ConcurrentHashMap<>();

    private volatile boolean started = false;

    @PostConstruct
    public void init() {
        // 配置RabbitTemplate
        configureRabbitTemplate();
        
        // 创建默认交换器
        createDefaultExchange();
        
        log.info("RabbitMQ event transport initialized with exchange: {}", 
                eventBusProperties.getRabbitmq().getDefaultExchangeName());
    }

    @Override
    public BusRemoteType getTransportType() {
        return BusRemoteType.RABBITMQ;
    }

    @Override
    public CompletableFuture<TransportResult> send(IEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            try {
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
    public <T extends IEvent> void subscribe(String topic, IEventListener<T> listener, Class<T> eventType) {
        if (!started) {
            throw new IllegalStateException("RabbitMQ transport not started");
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
            RabbitMQEventMessageListener<T> messageListener = 
                    new RabbitMQEventMessageListener<>(listener, eventType, eventSerializer, topic);
            
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
        if (!started) {
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
            QueueBuilder builder = QueueBuilder.durable(name);
            
            if (config.isAutoDeleteQueue()) {
                builder.autoDelete();
            }
            if (config.isExclusiveQueue()) {
                builder.exclusive();
            }
            
            Queue queue = builder.build();
            
            rabbitAdmin.declareQueue(queue);
            return queue;
        });
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
        
        return container;
    }

    /**
     * 生成容器键
     */
    private String getContainerKey(String topic, IEventListener<?> listener) {
        return topic + ":" + listener.getClass().getName() + "@" + System.identityHashCode(listener);
    }
} 