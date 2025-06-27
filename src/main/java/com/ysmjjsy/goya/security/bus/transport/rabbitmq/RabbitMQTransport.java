package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.rabbitmq.client.Channel;
import com.ysmjjsy.goya.security.bus.encry.MessageEncryptor;
import com.ysmjjsy.goya.security.bus.enums.BusinessPriority;
import com.ysmjjsy.goya.security.bus.enums.MessageCapability;
import com.ysmjjsy.goya.security.bus.enums.MessageModel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategy;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportMessage;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强的RabbitMQ传输层实现
 * <p>
 * 支持完整的Exchange/Queue/Binding架构：
 * - QUEUE模式：使用Direct Exchange实现点对点消息传递
 * - TOPIC模式：使用Topic Exchange实现发布/订阅模式
 * - BROADCAST模式：使用Fanout Exchange实现广播模式
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
public class RabbitMQTransport implements MessageTransport {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ConnectionFactory connectionFactory;
    private final Map<String, SimpleMessageListenerContainer> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean healthy = true;
    private final RoutingStrategy routingStrategy;

    public RabbitMQTransport(RabbitTemplate rabbitTemplate,
                             ConnectionFactory connectionFactory,
                             RoutingStrategy routingStrategy) {
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
        this.routingStrategy = routingStrategy;
        // 初始化Exchange
        initialize();
        checkHealth();

        log.info("Enhanced RabbitMQ transport initialized with Exchange/Queue/Binding support");
    }

    /**
     * 初始化
     */
    private void initialize() {
    }

    @Override
    public TransportResult send(TransportMessage message) {
        try {
            // 解析消息模型
            MessageModel messageModel = message.getRoutingContext().getMessageModel();

            // 处理延迟消息
            if (message.getDeliverTime() != null) {
                return sendDelayedMessage(message);
            }

            // 根据消息模型选择发送策略
            switch (messageModel) {
                case QUEUE:
                    return sendToQueue(message);
                case TOPIC:
                    return sendToTopic(message);
                case BROADCAST:
                    return sendToBroadcast(message);
                default:
                    return sendToQueue(message);
            }

        } catch (Exception e) {
            log.error("Failed to send message via RabbitMQ: {}", message.getMessageId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到队列（Direct Exchange）
     */
    private TransportResult sendToQueue(TransportMessage message) {
        try {
            // 获取路由上下文
            RoutingContext routingContext = message.getRoutingContext();

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String routingKey = routingContext.getRoutingSelector();
            String queueName = routingContext.getConsumerGroup();
            log.debug("Using routing context: exchange={}, routingKey={}, queue={}",
                    exchangeName, routingKey, queueName);

            // 发送消息到Direct Exchange
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    message.getBody(),
                    msg -> {
                        setMessageProperties(msg, message);
                        return msg;
                    }
            );

            log.debug("Message sent to queue: exchange={}, routingKey={}, queue={}, messageId={}",
                    exchangeName, routingKey, queueName, message.getMessageId());
            return TransportResult.success(message.getMessageId());

        } catch (Exception e) {
            log.error("Failed to send message to queue: {}", message.getMessageId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到主题（Topic Exchange）
     */
    private TransportResult sendToTopic(TransportMessage message) {
        try {
            // 获取路由上下文
            RoutingContext routingContext = message.getRoutingContext();

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String routingKey = routingContext.getRoutingSelector();
            log.debug("Using routing context for topic: exchange={}, routingKey={}",
                    exchangeName, routingKey);


            // 发送消息到Topic Exchange
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    message.getBody(),
                    msg -> {
                        setMessageProperties(msg, message);
                        return msg;
                    }
            );

            log.debug("Message sent to topic: exchange={}, routingKey={}, messageId={}",
                    exchangeName, routingKey, message.getMessageId());
            return TransportResult.success(message.getMessageId());

        } catch (Exception e) {
            log.error("Failed to send message to topic: {}", message.getMessageId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到广播（Fanout Exchange）
     */
    private TransportResult sendToBroadcast(TransportMessage message) {
        try {
            // 获取路由上下文
            RoutingContext routingContext = message.getRoutingContext();

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String routingKey = "";

            log.debug("Using routing context for broadcast: exchange={}", exchangeName);

            // 发送消息到Fanout Exchange（无需路由键）
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    message.getBody(),
                    msg -> {
                        setMessageProperties(msg, message);
                        return msg;
                    }
            );

            log.debug("Message sent to broadcast: exchange={}, messageId={}",
                    exchangeName, message.getMessageId());
            return TransportResult.success(message.getMessageId());

        } catch (Exception e) {
            log.error("Failed to send message to broadcast: {}", message.getMessageId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送延迟消息
     * TTL+私信队列
     */
    private TransportResult sendDelayedMessage(TransportMessage message) {
        try {
            // 计算延迟时间
            long now = System.currentTimeMillis();
            long deliverTimeMs = message.getDeliverTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long delayMs = deliverTimeMs - now;

            if (delayMs <= 0) {
                log.warn("Deliver time is in the past, sending immediately: {}", message.getMessageId());
                return send(message);
            }

            // 构建延迟路由键
            String originalRoutingKey = message.getRoutingContext().getRoutingSelector();
            String delayRoutingKey = "delay." + delayMs + "." + originalRoutingKey;

            // 创建延迟队列并绑定
            final String delayExchangeName = message.getRoutingContext().getBusinessDomain() + "-" + "delay";

            String delayQueueName = createDelayQueue(delayExchangeName, delayMs, delayRoutingKey, message);

            // 发送到死信队列
            rabbitTemplate.convertAndSend(
                    delayExchangeName,
                    delayRoutingKey,
                    message.getBody(),
                    msg -> {
                        setMessageProperties(msg, message);
                        // 设置消息TTL
                        msg.getMessageProperties().setExpiration(String.valueOf(delayMs));
                        return msg;
                    }
            );

            log.debug("Delayed message sent: delay={}ms, delayQueue={}, messageId={}",
                    delayMs, delayQueueName, message.getMessageId());
            return TransportResult.success(message.getMessageId());

        } catch (Exception e) {
            log.error("Failed to send delayed message: {}", message.getMessageId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            // 解析消息模型
            MessageModel messageModel = config.getMessageModel();

            // 根据消息模型创建订阅
            switch (messageModel) {
                case QUEUE:
                    subscribeToQueue(config, consumer);
                    break;
                case TOPIC:
                    subscribeToTopic(config, consumer);
                    break;
                case BROADCAST:
                    subscribeToBroadcast(config, consumer);
                    break;
                default:
                    subscribeToQueue(config, consumer);
            }

        } catch (Exception e) {
            log.error("Failed to subscribe to RabbitMQ with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to RabbitMQ", e);
        }
    }

    /**
     * 订阅队列模式（Direct Exchange）
     */
    private void subscribeToQueue(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            // 使用路由策略管理器构建订阅路由上下文
            RoutingContext routingContext = routingStrategy.buildSubscriptionContext(config);

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String queueName = routingContext.getConsumerGroup();
            String routingKey = routingContext.getRoutingSelector();

            log.debug("Using routing context for subscription: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey);


            String subscriptionId = exchangeName + "_" + queueName;

            // 确保队列和绑定存在
            ensureQueueBinding(exchangeName, queueName, routingKey, MessageModel.QUEUE);

            // 创建监听器容器
            createListenerContainer(subscriptionId, queueName, config, consumer);

            log.info("Subscribed to queue: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey);

        } catch (Exception e) {
            log.error("Failed to subscribe to queue with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to queue", e);
        }
    }

    /**
     * 订阅主题模式（Topic Exchange）
     */
    private void subscribeToTopic(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            // 使用路由策略管理器构建订阅路由上下文
            RoutingContext routingContext = routingStrategy.buildSubscriptionContext(config);

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String queueName = routingContext.getConsumerGroup();
            String routingKey = routingContext.getRoutingSelector();

            log.debug("Using routing context for topic subscription: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey);


            String subscriptionId = exchangeName + "_" + queueName;

            // 确保队列和绑定存在
            ensureTopicBinding(exchangeName, queueName, routingKey);

            // 创建监听器容器
            createListenerContainer(subscriptionId, queueName, config, consumer);

            log.info("Subscribed to topic: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey);

        } catch (Exception e) {
            log.error("Failed to subscribe to topic with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to topic", e);
        }
    }

    /**
     * 订阅广播模式（Fanout Exchange）
     */
    private void subscribeToBroadcast(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            // 使用路由策略管理器构建订阅路由上下文
            RoutingContext routingContext = routingStrategy.buildSubscriptionContext(config);

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String queueName = routingContext.getConsumerGroup();

            log.debug("Using routing context for broadcast subscription: exchange={}, queue={}",
                    exchangeName, queueName);

            String subscriptionId = exchangeName + "_" + queueName;

            // 确保队列和绑定存在
            ensureBroadcastBinding(exchangeName, queueName);

            // 创建监听器容器
            createListenerContainer(subscriptionId, queueName, config, consumer);

            log.info("Subscribed to broadcast: exchange={}, queue={}",
                    exchangeName, queueName);

        } catch (Exception e) {
            log.error("Failed to subscribe to broadcast with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to broadcast", e);
        }
    }

    /**
     * 确保队列绑定（Direct Exchange）
     */
    private void ensureQueueBinding(String exchangeName, String queueName, String routingKey, MessageModel messageModel) {
        try {

            // 声明Exchange
            DirectExchange exchange = new DirectExchange(exchangeName, true, false);
            rabbitAdmin.declareExchange(exchange);

            // 声明队列
            Queue queue = QueueBuilder.durable(queueName).build();
            rabbitAdmin.declareQueue(queue);

            // 绑定队列到Exchange
            Binding binding = BindingBuilder.bind(queue)
                    .to(exchange)
                    .with(routingKey);
            rabbitAdmin.declareBinding(binding);

            log.debug("Ensured queue binding: queue={}, exchange={}, routingKey={}",
                    queueName, exchangeName, routingKey);

        } catch (Exception e) {
            log.error("Failed to ensure queue binding: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey, e);
            throw new RuntimeException("Failed to ensure queue binding", e);
        }
    }

    /**
     * 确保主题绑定（Topic Exchange）
     */
    private void ensureTopicBinding(String exchangeName, String queueName, String routingKey) {
        try {
            log.debug("ensureTopicBinding called with: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey);

            // 检查参数是否为null
            if (exchangeName == null || exchangeName.isEmpty()) {
                throw new IllegalArgumentException("ExchangeName cannot be null or empty for topic binding");
            }
            if (queueName == null || queueName.isEmpty()) {
                throw new IllegalArgumentException("QueueName cannot be null or empty for topic binding");
            }
            if (routingKey == null) {
                throw new IllegalArgumentException("RoutingKey cannot be null for topic binding");
            }

            // 声明Exchange
            TopicExchange exchange = new TopicExchange(exchangeName, true, false);
            rabbitAdmin.declareExchange(exchange);

            // 声明队列
            Queue queue = QueueBuilder.durable(queueName).build();
            rabbitAdmin.declareQueue(queue);

            // 绑定队列到Topic Exchange
            Binding binding = BindingBuilder.bind(queue)
                    .to(exchange)
                    .with(routingKey);
            rabbitAdmin.declareBinding(binding);

            log.debug("Ensured topic binding: queue={}, exchange={}, routingKey={}",
                    queueName, exchangeName, routingKey);

        } catch (Exception e) {
            log.error("Failed to ensure topic binding: exchange={}, queue={}, routingKey={}",
                    exchangeName, queueName, routingKey, e);
            throw new RuntimeException("Failed to ensure topic binding", e);
        }
    }

    /**
     * 确保广播绑定（Fanout Exchange）
     */
    private void ensureBroadcastBinding(String exchangeName, String queueName) {
        try {
            log.debug("ensureBroadcastBinding called with: exchange={}, queue={}",
                    exchangeName, queueName);

            // 检查参数是否为null
            if (exchangeName == null || exchangeName.isEmpty()) {
                throw new IllegalArgumentException("ExchangeName cannot be null or empty for broadcast binding");
            }
            if (queueName == null || queueName.isEmpty()) {
                throw new IllegalArgumentException("QueueName cannot be null or empty for broadcast binding");
            }

            // 声明Exchange
            FanoutExchange exchange = new FanoutExchange(exchangeName, true, false);
            rabbitAdmin.declareExchange(exchange);

            // 声明队列
            Queue queue = QueueBuilder.durable(queueName).build();
            rabbitAdmin.declareQueue(queue);

            // 绑定到Fanout Exchange（无需路由键）
            Binding binding = BindingBuilder.bind(queue)
                    .to(exchange);
            rabbitAdmin.declareBinding(binding);

            log.debug("Ensured broadcast binding: queue={}, exchange={}",
                    queueName, exchangeName);

        } catch (Exception e) {
            log.error("Failed to ensure broadcast binding: exchange={}, queue={}",
                    exchangeName, queueName, e);
            throw new RuntimeException("Failed to ensure broadcast binding", e);
        }
    }

    /**
     * 创建延迟队列
     */
    private String createDelayQueue(String delayExchangeName, long delayMs, String delayRoutingKey, TransportMessage message) {
        try {
            String targetQueueName = message.getRoutingContext().getConsumerGroup();
            String targetExchange = message.getRoutingContext().getBusinessDomain();
            String targetRoutingKey = message.getRoutingContext().getRoutingSelector();

            // 声明延迟队列（带TTL和死信设置）
            Queue delayQueue = QueueBuilder.durable(targetQueueName)
                    .withArgument("x-message-ttl", delayMs)
                    .withArgument("x-dead-letter-exchange", targetExchange)
                    .withArgument("x-dead-letter-routing-key", targetRoutingKey)
                    .build();
            rabbitAdmin.declareQueue(delayQueue);

            // 绑定延迟队列到延迟Exchange
            Binding delayBinding = BindingBuilder.bind(delayQueue)
                    .to(new TopicExchange(delayExchangeName))
                    .with(delayRoutingKey);
            rabbitAdmin.declareBinding(delayBinding);

            log.debug("Created delay queue: {} -> {} (delay: {}ms)",
                    targetQueueName, targetExchange, delayMs);

            return targetQueueName;

        } catch (Exception e) {
            log.error("Failed to create delay queue for routing key: {}", message.getRoutingContext().getRoutingSelector(), e);
            throw new RuntimeException("Failed to create delay queue", e);
        }
    }

    /**
     * 创建监听器容器
     */
    private void createListenerContainer(String subscriptionId, String queueName,
                                         SubscriptionConfig config, MessageConsumer consumer) {
        try {
            // 创建消息监听器容器
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setQueueNames(queueName);
            container.setConcurrentConsumers(config.getConcurrency());
            container.setMaxConcurrentConsumers(config.getConcurrency() * 2);
            // 手动确认模式
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);

            // 创建专门的RabbitMQ事件消息监听器
            RabbitMQEventMessageListener messageListener = new RabbitMQEventMessageListener(consumer, config);
            container.setMessageListener(messageListener);
            container.start();

            // 保存订阅信息
            subscriptions.put(subscriptionId, container);

            log.debug("Created listener container: subscriptionId={}, queue={}, concurrency={}",
                    subscriptionId, queueName, config.getConcurrency());

        } catch (Exception e) {
            log.error("Failed to create listener container: subscriptionId={}, queue={}",
                    subscriptionId, queueName, e);
            throw new RuntimeException("Failed to create listener container", e);
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy && connectionFactory != null;
    }

    /**
     * 主动健康检查
     */
    private void checkHealth() {
        try {
            // 尝试获取连接来验证健康状态
            connectionFactory.createConnection().close();
            healthy = true;
            log.debug("RabbitMQ transport health check passed");
        } catch (Exception e) {
            healthy = false;
            log.warn("RabbitMQ transport health check failed", e);
        }
    }

    /**
     * 执行健康检查
     */
    public void performHealthCheck() {
        checkHealth();
    }

    @Override
    public TransportType getTransportType() {
        return TransportType.RABBITMQ;
    }

    @Override
    public Set<MessageCapability> getSupportedCapabilities() {
        return Set.of(
                MessageCapability.DELAYED_MESSAGE,
                MessageCapability.TRANSACTIONAL_MESSAGE,
                MessageCapability.BATCH_MESSAGE,
                MessageCapability.PERSISTENT_MESSAGE,
                MessageCapability.DEAD_LETTER_QUEUE,
                MessageCapability.CLUSTER_SUPPORT

        );
    }

    /**
     * 设置消息属性
     */
    private void setMessageProperties(Message msg, TransportMessage message) {
        // 设置消息头
        if (message.getHeaders() != null) {
            message.getHeaders().forEach((key, value) ->
                    msg.getMessageProperties().getHeaders().put(key, value));
        }

        // 设置消息属性
        msg.getMessageProperties().setMessageId(message.getMessageId());
        if (message.getPriority() != null) {
            msg.getMessageProperties().setPriority(message.getPriority());
        }

        if (message.getTtl() != null) {
            msg.getMessageProperties().setExpiration(String.valueOf(message.getTtl()));
        }

        // 设置业务优先级
        if (message.getBusinessPriority() != null) {
            msg.getMessageProperties().getHeaders().put("businessPriority", message.getBusinessPriority().name());
        }
    }

    /**
     * RabbitMQ 专用事件消息监听器
     * 实现 ChannelAwareMessageListener 以支持手动确认和完整的错误处理
     */
    private class RabbitMQEventMessageListener implements ChannelAwareMessageListener {

        private final MessageConsumer consumer;
        private final SubscriptionConfig config;

        public RabbitMQEventMessageListener(MessageConsumer consumer, SubscriptionConfig config) {
            this.consumer = consumer;
            this.config = config;
        }

        @Override
        public void onMessage(Message message, Channel channel) throws Exception {
            String messageId = message.getMessageProperties().getMessageId();
            long deliveryTag = message.getMessageProperties().getDeliveryTag();

            try {
                log.debug("Received message from RabbitMQ: {}", messageId);

                // 构建TransportMessage
                TransportMessage transportMessage = buildTransportMessage(message);

                // 调用消费者处理消息
                consumer.consume(transportMessage);

                // 手动确认消息
                channel.basicAck(deliveryTag, false);
                log.debug("Message acknowledged successfully: {}", messageId);

            } catch (Exception e) {
                log.error("Failed to process message: {}", messageId, e);

                // 处理消息失败
                handleMessageFailure(message, channel, deliveryTag, e);
            }
        }

        /**
         * 构建传输消息对象
         */
        private TransportMessage buildTransportMessage(Message message) {
            MessageProperties props = message.getMessageProperties();
            byte[] body = message.getBody();

            // 处理解密
            boolean encrypted = Boolean.TRUE.equals(props.getHeaders().get("encrypted"));
            if (encrypted) {
                String encryptionType = (String) props.getHeaders().get("encryptionType");
                if ("aes128".equals(encryptionType)) {
                    try {
                        MessageEncryptor encryptor = new MessageEncryptor();
                        body = encryptor.decrypt(body, MessageEncryptor.EncryptionType.AES_128);
                        log.debug("Message decrypted successfully");
                    } catch (Exception e) {
                        log.error("Failed to decrypt message: {}", props.getMessageId(), e);
                    }
                }
            }

            // 解析业务优先级
            BusinessPriority businessPriority = null;
            String priorityStr = (String) props.getHeaders().get("businessPriority");
            if (priorityStr != null) {
                try {
                    businessPriority = BusinessPriority.valueOf(priorityStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid business priority: {}", priorityStr);
                }
            }

            return TransportMessage.builder()
                    .messageId(props.getMessageId())
                    .body(body)
                    .originalBodySize((Integer) props.getHeaders().get("originalSize"))
                    .headers(props.getHeaders())
                    .messageType(props.getType())
                    .priority(props.getPriority() != null ? props.getPriority() : 0)
                    .businessPriority(businessPriority)
                    .enableEncryption(encrypted)
                    .performanceSensitive(Boolean.TRUE.equals(props.getHeaders().get("performanceSensitive")))
                    .ttl(props.getExpiration() != null ? Long.parseLong(props.getExpiration()) : null)
                    .build();
        }

        /**
         * 处理消息失败
         */
        private void handleMessageFailure(Message message, Channel channel, long deliveryTag, Exception e) {
            try {
                MessageProperties props = message.getMessageProperties();
                String messageId = props.getMessageId();

                // 获取重试次数
                Integer retryCount = getRetryCount(props);
                int maxRetries = 3; // 可以从配置中获取

                if (retryCount < maxRetries) {
                    // 重试：拒绝消息并重新入队
                    retryCount++;
                    props.getHeaders().put("x-retry-count", retryCount);

                    channel.basicNack(deliveryTag, false, true);
                    log.warn("Message processing failed, will retry ({}/{}): {} - {}",
                            retryCount, maxRetries, messageId, e.getMessage());
                } else {
                    // 超过最大重试次数：拒绝消息且不重新入队（进入死信队列）
                    channel.basicNack(deliveryTag, false, false);
                    log.error("Message processing failed after {} retries, moved to dead letter: {} - {}",
                            maxRetries, messageId, e.getMessage());
                }

            } catch (Exception channelException) {
                log.error("Failed to handle message failure for delivery tag: {}", deliveryTag, channelException);
            }
        }

        /**
         * 获取消息重试次数
         */
        private Integer getRetryCount(MessageProperties props) {
            Object retryCountObj = props.getHeaders().get("x-retry-count");
            if (retryCountObj instanceof Integer) {
                return (Integer) retryCountObj;
            }
            return 0;
        }
    }
} 