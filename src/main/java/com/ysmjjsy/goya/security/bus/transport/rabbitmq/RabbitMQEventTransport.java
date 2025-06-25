package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import cn.hutool.extra.spring.SpringUtil;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.PropertyResolver;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.processor.MethodIEventListenerWrapper;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import com.ysmjjsy.goya.security.bus.transport.TransportResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ 事件传输实现
 * 使用 RabbitMQ Topic Exchange 实现事件传输
 * <p>
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
@RequiredArgsConstructor
public class RabbitMQEventTransport implements EventTransport {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ConnectionFactory connectionFactory;
    private final EventSerializer eventSerializer;
    private final BusProperties eventBusProperties;
    private final RabbitMqConfigResolver configResolver;

    private final RabbitMQManagementTool managementTool;
    private final DelayQueueManager delayQueueManager;

    private final Map<String, AbstractExchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, Map<IEventListener<?>, RabbitMQEventMessageListener<?>>> subscriptions = new ConcurrentHashMap<>();

    // 死信队列相关
    private TopicExchange deadLetterExchange;
    private Queue retryQueue;
    private final Map<String, Integer> retryCounters = new ConcurrentHashMap<>();

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
                if (!eventBusProperties.getRabbitmq().isEnabled()) {
                    return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(),
                            new IllegalStateException("RabbitMQ transport is disabled"));
                }

                if (!check(event)) {
                    return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(),
                            new EventHandleException(event.getEventId(), "Event is local event"));
                }

                // 检查是否为延迟消息
                if (event instanceof RabbitMqEvent && ((RabbitMqEvent<?>) event).isDelayEnabled()) {
                    return sendDelayedEvent((RabbitMqEvent<?>) event);
                }

                // 正常消息发送逻辑
                return sendNormalEvent(event);

            } catch (Exception e) {
                log.error("Failed to send event {} to RabbitMQ topic '{}'", event.getEventId(), event.getTopic(), e);
                return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(), e);
            }
        });
    }

    /**
     * 发送延迟消息
     */
    private TransportResult sendDelayedEvent(RabbitMqEvent<?> event) {
        try {
            // 检查延迟队列功能是否启用
            if (!delayQueueManager.isDelayEnabled()) {
                log.warn("延迟队列功能未启用，将按正常消息发送: {}", event.getEventId());
                return sendNormalEvent(event);
            }

            // 验证延迟时间
            delayQueueManager.validateDelayTime(event.getDelayMillis());

            // 序列化事件
            String serializedEvent = eventSerializer.serialize(event);

            // 获取目标交换器和路由键（延迟后要投递到的地方）
            String targetExchange = event.getEffectiveDelayTargetExchange();
            String targetRoutingKey = event.getEffectiveDelayTargetRoutingKey(event.getTopic());

            // 获取延迟队列路由键
            String delayRoutingKey = delayQueueManager.getDelayQueueRoutingKey(
                event.getDelaySeconds(), targetExchange, targetRoutingKey);

            // 发送到延迟交换器
            String delayExchangeName = delayQueueManager.getDelayExchangeName();
            rabbitTemplate.convertAndSend(delayExchangeName, delayRoutingKey, serializedEvent, 
                message -> applyDelayedMessageProperties(message, event, targetExchange, targetRoutingKey));

            log.info("延迟消息已发送到延迟队列: eventId={}, delayMillis={}, targetExchange={}, targetRoutingKey={}", 
                event.getEventId(), event.getDelayMillis(), targetExchange, targetRoutingKey);

            return TransportResult.success(getTransportType(), event.getTopic(), event.getEventId());

        } catch (Exception e) {
            log.error("发送延迟消息失败: eventId={}, delayMillis={}", event.getEventId(), event.getDelayMillis(), e);
            throw e;
        }
    }

    /**
     * 发送正常消息（非延迟）
     */
    private TransportResult sendNormalEvent(IEvent<?> event) {
        // 解析配置
        RabbitMqConfigModel config = resolveEventConfig(event);

        // 序列化事件
        String serializedEvent = eventSerializer.serialize(event);

        // 获取有效的交换器和路由键
        String exchangeName = config.getEffectiveExchange();
        String routingKey = config.getEffectiveRoutingKey(event.getTopic());

        // 确保交换器存在（如果指定了非默认交换器）
        if (exchangeName != null) {
            getOrCreateExchangeWithConfig(config);
        }

        // 发送消息 - 使用Spring Boot RabbitMQ的标准方式
        rabbitTemplate.convertAndSend(exchangeName, routingKey, serializedEvent, 
            message -> applyMessageProperties(message, event, config));

        log.debug("Event sent to RabbitMQ exchange '{}' with routing key '{}': {}",
                exchangeName != null ? exchangeName : "default", routingKey, event.getEventId());

        return TransportResult.success(getTransportType(), event.getTopic(), event.getEventId());
    }

    /**
     * 应用延迟消息属性
     */
    private Message applyDelayedMessageProperties(Message message, RabbitMqEvent<?> event, 
            String targetExchange, String targetRoutingKey) {
        MessageProperties properties = message.getMessageProperties();

        // 设置基础属性
        properties.setContentType("application/json");
        properties.setMessageId(event.getEventId());
        properties.setTimestamp(new Date());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT); // 延迟消息强制持久化

        // 设置延迟相关头部信息
        properties.setHeader("delay-enabled", true);
        properties.setHeader("delay-millis", event.getDelayMillis());
        properties.setHeader("original-topic", event.getTopic());
        properties.setHeader("event-type", event.getEventType());
        properties.setHeader("target-exchange", targetExchange);
        properties.setHeader("target-routing-key", targetRoutingKey);

        // 设置其他消息属性（优先级、关联ID等）
        if (event.getPriority() > 0) {
            properties.setPriority(event.getPriority());
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(event.getCorrelationId())) {
            properties.setCorrelationId(event.getCorrelationId());
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(event.getReplyTo())) {
            properties.setReplyTo(event.getReplyTo());
        }

        return message;
    }

    /**
     * 应用消息属性 - 基于Spring Boot RabbitMQ的MessageProperties
     */
    private Message applyMessageProperties(Message message, IEvent<?> event, RabbitMqConfigModel config) {
        MessageProperties properties = message.getMessageProperties();

        // 设置基础属性
        properties.setContentType("application/json");
        properties.setMessageId(event.getEventId());
        properties.setTimestamp(new Date());

        // 设置传递模式
        if (config.shouldSetDeliveryMode()) {
            properties.setDeliveryMode(MessageDeliveryMode.fromInt(config.getDeliveryMode()));
        } else {
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT); // 默认持久化
        }

        // 设置TTL（消息过期时间）
        if (config.shouldSetTtl()) {
            properties.setExpiration(String.valueOf(config.getMessageTtl()));
        }

        // 设置消息优先级
        if (config.shouldSetPriority()) {
            properties.setPriority(config.getPriority());
        }

        // 设置关联ID
        if (config.shouldSetCorrelationId()) {
            properties.setCorrelationId(config.getCorrelationId());
        }

        // 设置回复地址
        if (config.shouldSetReplyTo()) {
            properties.setReplyTo(config.getReplyTo());
        }

        // 设置业务重试相关头部（用于业务层重试控制）
        if (config.isRetryEnabled()) {
            properties.setHeader("retry-count", 0);
            properties.setHeader("max-retry-attempts", config.getRetryAttempts());
            properties.setHeader("retry-interval", config.getRetryInterval());
        }

        // 设置原始主题信息
        properties.setHeader("original-topic", event.getTopic());
        properties.setHeader("event-type", event.getEventType());

        return message;
    }

    @Override
    public <E extends IEvent<E>> void subscribe(String topic, IEventListener<E> listener, Class<E> eventType) {
        if (!eventBusProperties.getRabbitmq().isEnabled()) {
            log.warn("RabbitMQ transport is disabled, subscription ignored for topic: {}", topic);
            return;
        }

        try {
            // 解析配置
            RabbitMqConfigModel config = resolveListenerConfig(listener, topic);

            // 确保交换器存在
            AbstractExchange exchange = getOrCreateExchangeWithConfig(config);

            // 创建队列
            Queue queue = createQueueForSubscriptionWithConfig(config, topic, listener);

            // 创建绑定
            String routingKey = config.getEffectiveRoutingKey(topic);
            Binding binding = createBindingForExchange(queue, exchange, routingKey);
            rabbitAdmin.declareBinding(binding);

            // 创建消息监听器
            boolean manualAck = config.getAcknowledgeMode() == AcknowledgeMode.MANUAL;
            RabbitMQEventMessageListener<E> messageListener =
                    new RabbitMQEventMessageListener<>(listener, eventType, eventSerializer, topic, this, manualAck);

            // 创建消息监听容器
            SimpleMessageListenerContainer container = createMessageListenerContainerWithConfig(queue, messageListener, config);

            // 保存订阅信息
            subscriptions.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                    .put(listener, messageListener);
            containers.put(getContainerKey(topic, listener), container);

            // 启动容器
            container.start();

            log.info("Subscribed to RabbitMQ topic '{}' for event type {} with queue '{}' (exchange: '{}', routing key: '{}')",
                    topic, eventType.getSimpleName(), queue.getName(), exchange.getName(), routingKey);

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

    }

    @Override
    public void stop() {
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
     * @param message   原始消息
     * @param topic     主题
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

        if (StringUtils.isNotBlank(config.getDeadLetterExchange())) {
            // 创建死信交换器
            deadLetterExchange = new TopicExchange(
                    config.getDeadLetterExchange(),
                    config.isDurableExchange(),
                    config.isAutoDeleteExchange()
            );
            rabbitAdmin.declareExchange(deadLetterExchange);

           String deadLetterQueueName = PropertyResolver.getApplicationName(SpringUtil.getApplicationContext().getEnvironment()) + ".bus.dead-letter";
            // 创建死信队列
            Queue deadLetterQueue = QueueBuilder
                    .durable(deadLetterQueueName)
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
        String retryQueueName = PropertyResolver.getApplicationName(SpringUtil.getApplicationContext().getEnvironment()) + ".bus.retry";

        try {
            Map<String, Object> args = new HashMap<>();
            args.put("x-message-ttl", config.getRetryInterval());
            args.put("x-dead-letter-exchange", config.getDefaultExchangeName());

            retryQueue = QueueBuilder
                    .durable(retryQueueName)
                    .withArguments(args)
                    .build();

            rabbitAdmin.declareQueue(retryQueue);
            log.debug("Retry queue '{}' created with TTL: {}ms", retryQueue.getName(), config.getRetryInterval());

        } catch (Exception e) {
            log.warn("Failed to create retry queue '{}' with full config, attempting cleanup and recreation: {}",
                    retryQueueName, e.getMessage());

            try {
                // 尝试删除现有的冲突队列
                rabbitAdmin.deleteQueue(retryQueueName);
                log.info("Deleted existing retry queue '{}' due to configuration conflict", retryQueueName);

                // 重新创建队列
                Map<String, Object> args = new HashMap<>();
                args.put("x-message-ttl", config.getRetryInterval());
                args.put("x-dead-letter-exchange", config.getDefaultExchangeName());

                retryQueue = QueueBuilder
                        .durable(retryQueueName)
                        .withArguments(args)
                        .build();

                rabbitAdmin.declareQueue(retryQueue);
                log.info("Successfully recreated retry queue '{}' with new configuration", retryQueueName);

            } catch (Exception ex) {
                log.error("Failed to recreate retry queue '{}', using simplified configuration: {}",
                        retryQueueName, ex.getMessage());

                // 最后尝试：创建没有参数的简化队列
                retryQueue = QueueBuilder.durable(retryQueueName).build();
                try {
                    rabbitAdmin.declareQueue(retryQueue);
                    log.warn("Created simplified retry queue '{}' without TTL configuration", retryQueueName);
                } catch (Exception finalEx) {
                    log.error("Failed to create any retry queue configuration: {}", finalEx.getMessage());
                    retryQueue = null;
                }
            }
        }
    }

    /**
     * 安排重试
     */
    private void scheduleRetry(Message message, String topic, long delay) {
        if (retryQueue == null) {
            log.error("Retry queue not available, cannot schedule retry for topic: {}", topic);
            return;
        }

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
     * 生成容器键
     */
    private String getContainerKey(String topic, IEventListener<?> listener) {
        return topic + ":" + listener.getClass().getName() + "@" + System.identityHashCode(listener);
    }

    /**
     * 获取重试队列名称
     * 用于SpEL表达式引用
     */
    public String getRetryQueueName() {
        return retryQueue != null ? retryQueue.getName() : "bus.retry";
    }

    /**
     * 解析事件配置
     */
    private RabbitMqConfigModel resolveEventConfig(IEvent<?> event) {
        if (event instanceof RabbitMqEvent) {
            return configResolver.resolveFromEvent((RabbitMqEvent<?>) event);
        }
        return configResolver.resolveFromDefaults(event.getTopic());
    }

    /**
     * 解析监听器配置
     */
    private RabbitMqConfigModel resolveListenerConfig(IEventListener<?> listener, String topic) {
        if (listener instanceof MethodIEventListenerWrapper) {
            MethodIEventListenerWrapper wrapper = (MethodIEventListenerWrapper) listener;
            RabbitMqConfig annotation = wrapper.getRabbitMqConfig();
            return configResolver.resolveFromAnnotation(annotation, topic);
        }
        return configResolver.resolveFromDefaults(topic);
    }

    /**
     * 使用配置创建或获取交换器
     */
    private AbstractExchange getOrCreateExchangeWithConfig(RabbitMqConfigModel config) {
        String exchangeName = config.getEffectiveExchange();

        // 先检查本地缓存
        AbstractExchange cachedExchange = exchanges.get(exchangeName);
        if (cachedExchange != null) {
            return cachedExchange;
        }

        // 直接创建交换器对象并声明（RabbitMQ 声明操作是幂等的）
        AbstractExchange exchange = createExchangeByType(exchangeName, config);
        try {
            rabbitAdmin.declareExchange(exchange);
            exchanges.put(exchangeName, exchange);
            log.debug("Declared {} exchange: {}", config.getEffectiveExchangeType(), exchangeName);
            return exchange;
        } catch (Exception e) {
            log.error("Failed to declare exchange '{}': {}", exchangeName, e.getMessage());
            throw new RuntimeException("Failed to declare exchange: " + exchangeName, e);
        }
    }

    /**
     * 根据类型创建交换器
     */
    private AbstractExchange createExchangeByType(String exchangeName, RabbitMqConfigModel config) {
        String exchangeType = config.getEffectiveExchangeType();
        boolean durable = config.isDurableExchange();
        boolean autoDelete = config.isAutoDeleteExchange();

        switch (exchangeType) {
            case "direct":
                return new DirectExchange(exchangeName, durable, autoDelete);
            case "fanout":
                return new FanoutExchange(exchangeName, durable, autoDelete);
            case "headers":
                return new HeadersExchange(exchangeName, durable, autoDelete);
            case "topic":
            default:
                return new TopicExchange(exchangeName, durable, autoDelete);
        }
    }

    /**
     * 使用配置为订阅创建队列
     */
    private Queue createQueueForSubscriptionWithConfig(RabbitMqConfigModel config, String topic, IEventListener<?> listener) {
        String queueName = generateQueueName(config, topic, listener);

        // 先检查本地缓存
        Queue cachedQueue = queues.get(queueName);
        if (cachedQueue != null) {
            return cachedQueue;
        }

        // 使用 RabbitMQ 服务检查队列是否已存在
        try {
            if (managementTool.queueExists(queueName)) {
                // 队列已存在，直接创建队列对象并缓存
                Queue existingQueue = new Queue(queueName, config.isDurableQueue());
                queues.put(queueName, existingQueue);
                log.debug("Queue '{}' already exists in RabbitMQ server, using existing queue", queueName);
                return existingQueue;
            }
        } catch (Exception e) {
            log.debug("Error checking queue existence for '{}': {}", queueName, e.getMessage());
        }

        // 队列不存在，需要创建
        try {
            Queue newQueue = createQueueWithConfig(queueName, config);
            queues.put(queueName, newQueue);
            return newQueue;
        } catch (Exception e) {
            log.warn("Failed to create queue '{}' with full config, trying with simplified config: {}", queueName, e.getMessage());
            try {
                Queue simplifiedQueue = createSimplifiedQueue(queueName, config);
                queues.put(queueName, simplifiedQueue);
                return simplifiedQueue;
            } catch (Exception ex) {
                log.error("Failed to create simplified queue '{}': {}", queueName, ex.getMessage());
                throw new RuntimeException("Failed to create queue: " + queueName, ex);
            }
        }
    }

    /**
     * 使用配置创建队列
     */
    private Queue createQueueWithConfig(String queueName, RabbitMqConfigModel config) {
        Map<String, Object> arguments = new HashMap<>();

        // 设置TTL
        if (config.getMessageTtl() > 0) {
            arguments.put("x-message-ttl", config.getMessageTtl());
        }

        Queue queue = new Queue(queueName, config.isDurableQueue(), false, false, arguments);
        rabbitAdmin.declareQueue(queue);
        log.debug("Created queue with config: {}", queueName);
        return queue;
    }

    /**
     * 创建简化的队列（当完整配置失败时）
     */
    private Queue createSimplifiedQueue(String queueName, RabbitMqConfigModel config) {
        Queue queue = new Queue(queueName, config.isDurableQueue());
        rabbitAdmin.declareQueue(queue);
        log.debug("Created simplified queue: {}", queueName);
        return queue;
    }

    /**
     * 使用配置创建消息监听容器
     */
    private SimpleMessageListenerContainer createMessageListenerContainerWithConfig(
            Queue queue, RabbitMQEventMessageListener<?> messageListener, RabbitMqConfigModel config) {

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(messageListener);

        // 设置确认模式
        container.setAcknowledgeMode(config.getAcknowledgeMode());

        // 设置预取数量
        container.setPrefetchCount(config.getPrefetchCount());

        // 设置并发消费者
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);

        return container;
    }

    /**
     * 生成队列名称
     */
    private String generateQueueName(RabbitMqConfigModel config, String topic, IEventListener<?> listener) {
        if (config.getQueueName() != null && !config.getQueueName().isEmpty()) {
            return config.getFullQueueName();
        }

        return topic;
    }

    /**
     * 为不同类型的交换器创建绑定
     */
    private Binding createBindingForExchange(Queue queue, AbstractExchange exchange, String routingKey) {
        if (exchange instanceof TopicExchange) {
            return BindingBuilder.bind(queue).to((TopicExchange) exchange).with(routingKey);
        } else if (exchange instanceof DirectExchange) {
            return BindingBuilder.bind(queue).to((DirectExchange) exchange).with(routingKey);
        } else if (exchange instanceof FanoutExchange) {
            return BindingBuilder.bind(queue).to((FanoutExchange) exchange);
        } else if (exchange instanceof HeadersExchange) {
            // 对于Headers交换器，需要使用头部匹配而不是路由键
            return BindingBuilder.bind(queue).to((HeadersExchange) exchange).where("topic").exists();
        } else {
            // 默认按Topic处理
            return BindingBuilder.bind(queue).to((TopicExchange) exchange).with(routingKey);
        }
    }
} 