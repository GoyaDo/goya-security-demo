package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import cn.hutool.core.map.MapUtil;
import com.rabbitmq.client.Channel;
import com.ysmjjsy.goya.security.bus.annotation.RabbitConfig;
import com.ysmjjsy.goya.security.bus.enums.*;
import com.ysmjjsy.goya.security.bus.exception.BusException;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.lang.annotation.Annotation;
import java.util.*;
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
    private final MessageSerializer messageSerializer;

    public RabbitMQTransport(RabbitTemplate rabbitTemplate,
                             RabbitAdmin rabbitAdmin,
                             ConnectionFactory connectionFactory,
                             MessageSerializer messageSerializer) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
        this.connectionFactory = connectionFactory;
        this.messageSerializer = messageSerializer;
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
    public TransportResult send(TransportEvent transportEvent) {
        try {
            // 解析消息模型
            EventModel eventModel = transportEvent.getEventModel();

            // 处理延迟消息
            if (transportEvent.getDelayTime() != null) {
                return sendDelayedMessage(transportEvent);
            }

            // 根据消息模型选择发送策略
            switch (eventModel) {
                case TOPIC:
                    return sendToTopic(transportEvent);
                case BROADCAST:
                    return sendToBroadcast(transportEvent);
                case QUEUE:
                default:
                    return sendToQueue(transportEvent);
            }

        } catch (Exception e) {
            log.error("Failed to send message via RabbitMQ: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到队列（Direct Exchange）
     */
    private TransportResult sendToQueue(TransportEvent message) {
        try {
            // 获取路由上下文
            RoutingContext routingContext = message.getRoutingContext();

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String routingKey = routingContext.getRoutingSelector();
            String queueName = routingContext.getConsumerGroup();
            log.debug("Using routing context: exchange={}, routingKey={}, queue={}",
                    exchangeName, routingKey, queueName);

            byte[] serialize = messageSerializer.serialize(message);
            // 发送消息到Direct Exchange
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    serialize,
                    msg -> {
                        setMessageProperties(msg, message);
                        return msg;
                    }
            );

            log.debug("Message sent to queue: exchange={}, routingKey={}, queue={}, messageId={}",
                    exchangeName, routingKey, queueName, message.getEventId());
            return TransportResult.success(message.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to queue: {}", message.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到主题（Topic Exchange）
     */
    private TransportResult sendToTopic(TransportEvent message) {
        try {
            // 获取路由上下文
            RoutingContext routingContext = message.getRoutingContext();

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String routingKey = routingContext.getRoutingSelector();
            log.debug("Using routing context for topic: exchange={}, routingKey={}",
                    exchangeName, routingKey);

            byte[] serialize = messageSerializer.serialize(message);

            // 发送消息到Topic Exchange
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    serialize,
                    msg -> {
                        setMessageProperties(msg, message);
                        return msg;
                    }
            );

            log.debug("Message sent to topic: exchange={}, routingKey={}, messageId={}",
                    exchangeName, routingKey, message.getEventId());
            return TransportResult.success(message.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to topic: {}", message.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到广播（Fanout Exchange）
     */
    private TransportResult sendToBroadcast(TransportEvent message) {
        try {
            // 获取路由上下文
            RoutingContext routingContext = message.getRoutingContext();

            // 使用新的路由系统
            String exchangeName = routingContext.getBusinessDomain();
            String routingKey = "";

            log.debug("Using routing context for broadcast: exchange={}", exchangeName);

            byte[] serialize = messageSerializer.serialize(message);
            // 发送消息到Fanout Exchange（无需路由键）
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    serialize,
                    msg -> {
                        setMessageProperties(msg, message);
                        return msg;
                    }
            );

            log.debug("Message sent to broadcast: exchange={}, messageId={}",
                    exchangeName, message.getEventId());
            return TransportResult.success(message.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to broadcast: {}", message.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送延迟消息
     * TTL+私信队列
     */
    private TransportResult sendDelayedMessage(TransportEvent message) {
        try {
            // 计算延迟时间
            long delayMs = message.getDelayTime().toMillis();

            if (delayMs <= 0) {
                log.warn("Deliver time is in the past, sending immediately: {}", message.getEventId());
                return send(message);
            }

            // 构建延迟路由键
            String originalRoutingKey = message.getRoutingContext().getRoutingSelector();
            String delayRoutingKey = "delay." + delayMs + "." + originalRoutingKey;

            // 创建延迟队列并绑定
            final String delayExchangeName = message.getRoutingContext().getBusinessDomain() + "-delay";

            String delayQueueName = createDelayQueue(delayExchangeName, delayMs, delayRoutingKey, message);

            byte[] serialize = messageSerializer.serialize(message);
            // 发送到死信队列
            rabbitTemplate.convertAndSend(
                    delayExchangeName,
                    delayRoutingKey,
                    serialize,
                    msg -> {
                        setMessageProperties(msg, message);
                        // 设置消息TTL
                        msg.getMessageProperties().setExpiration(String.valueOf(delayMs));
                        return msg;
                    }
            );

            log.debug("Delayed message sent: delay={}ms, delayQueue={}, messageId={}",
                    delayMs, delayQueueName, message.getEventId());
            return TransportResult.success(message.getEventId());

        } catch (Exception e) {
            log.error("Failed to send delayed message: {}", message.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            // 解析消息模型
            EventModel messageModel = config.getEventModel();

            Map<String, Object> properties = config.getProperties();
            boolean exchangeDurable = true;
            boolean exchangeAutoDelete = true;
            boolean queueDurable = true;
            boolean queueAutoDelete = true;


            if (MapUtil.isNotEmpty(properties)) {
                exchangeDurable = (boolean) properties.get(RabbitMQConstants.EXCHANGE_DURABLE);
                exchangeAutoDelete = (boolean) properties.get(RabbitMQConstants.EXCHANGE_AUTO_DELETE);
                queueDurable = (boolean) properties.get(RabbitMQConstants.QUEUE_DURABLE);
                queueAutoDelete = (boolean) properties.get(RabbitMQConstants.QUEUE_AUTO_DELETE);
            }

            String exchangeName = config.getRoutingContext().getBusinessDomain();
            String queueName = config.getRoutingContext().getConsumerGroup();
            String routingKey = config.getRoutingContext().getRoutingSelector();

            AbstractExchange exchange;
            // 根据消息模型创建订阅
            switch (messageModel) {
                case TOPIC:
                    exchange = createTopicExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
                    break;
                case BROADCAST:
                    exchange = createBroadExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
                    routingKey = "";
                    break;
                case QUEUE:
                default:
                    exchange = createQueueExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
                    break;
            }

            subscribeToQueue(exchange, consumer, queueName, routingKey, queueDurable, queueAutoDelete, properties);

        } catch (Exception e) {
            log.error("Failed to subscribe to RabbitMQ with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to RabbitMQ", e);
        }
    }

    private DirectExchange createQueueExchange(String exchangeName, boolean exchangeDurable, boolean exchangeAutoDelete) {
        // 声明Exchange
        DirectExchange exchange = new DirectExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
        rabbitAdmin.declareExchange(exchange);

        return exchange;
    }

    private TopicExchange createTopicExchange(String exchangeName, boolean exchangeDurable, boolean exchangeAutoDelete) {
        // 声明Exchange
        TopicExchange exchange = new TopicExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
        rabbitAdmin.declareExchange(exchange);
        return exchange;
    }

    private FanoutExchange createBroadExchange(String exchangeName, boolean exchangeDurable, boolean exchangeAutoDelete) {
        // 声明Exchange
        FanoutExchange exchange = new FanoutExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
        rabbitAdmin.declareExchange(exchange);
        return exchange;
    }

    /**
     * 订阅队列模式（Direct Exchange）
     */
    private void subscribeToQueue(AbstractExchange exchange,
                                  MessageConsumer consumer,
                                  String queueName,
                                  String routingKey,
                                  boolean queueDurable,
                                  boolean queueAutoDelete,
                                  Map<String, Object> properties) {
        try {

            String subscriptionId = queueName + "_" + routingKey;

            // 确保队列和绑定存在
            ensureQueueBinding(exchange, queueName, routingKey, queueDurable, queueAutoDelete, properties);

            Integer concurrency = 1;
            if (MapUtil.isNotEmpty(properties)) {
                concurrency = (Integer) properties.get(RabbitMQConstants.CONCURRENT_CONSUMERS);
            }
            // 创建监听器容器
            createListenerContainer(subscriptionId, queueName, concurrency, consumer);

            log.info("Subscribed to queue: exchange={}, queue={}, routingKey={}",
                    exchange.getName(), queueName, routingKey);

        } catch (Exception e) {
            throw new RuntimeException("Failed to subscribe to queue", e);
        }
    }

    /**
     * 确保队列绑定（Direct Exchange）
     */
    private void ensureQueueBinding(AbstractExchange exchange,
                                    String queueName,
                                    String routingKey,
                                    boolean queueDurable,
                                    boolean queueAutoDelete,
                                    Map<String, Object> properties) {
        try {
            // 声明队列
            QueueBuilder queueBuilder;
            if (queueDurable) {
                queueBuilder = QueueBuilder.durable(queueName);
            } else {
                queueBuilder = QueueBuilder.nonDurable(queueName);
            }
            if (queueAutoDelete) {
                queueBuilder.autoDelete();
            }

            if (MapUtil.isNotEmpty(properties)) {
                queueBuilder.withArguments(properties);
            }

            Queue queue = queueBuilder.build();
            rabbitAdmin.declareQueue(queue);

            // 绑定队列到Exchange
            Binding binding = BindingBuilder.bind(queue)
                    .to(exchange)
                    .with(routingKey).noargs();
            rabbitAdmin.declareBinding(binding);

        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure queue binding", e);
        }
    }


    /**
     * 创建监听器容器
     */
    private void createListenerContainer(String subscriptionId, String queueName,
                                         Integer concurrency, MessageConsumer consumer) {
        try {
            // 创建消息监听器容器
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setQueueNames(queueName);
            container.setConcurrentConsumers(concurrency);
            container.setMaxConcurrentConsumers(concurrency * 2);

            // 手动确认模式
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);

            // 创建专门的RabbitMQ事件消息监听器
            RabbitMQEventMessageListener messageListener = new RabbitMQEventMessageListener(consumer);
            container.setMessageListener(messageListener);
            container.start();

            // 保存订阅信息
            subscriptions.put(subscriptionId, container);

            log.debug("Created listener container: subscriptionId={}, queue={}",
                    subscriptionId, queueName);

        } catch (Exception e) {
            log.error("Failed to create listener container: subscriptionId={}, queue={}",
                    subscriptionId, queueName, e);
            throw new RuntimeException("Failed to create listener container", e);
        }
    }

    /**
     * 创建延迟队列
     */
    private String createDelayQueue(String delayExchangeName, long delayMs, String delayRoutingKey, TransportEvent message) {
        try {
            final String delayQueueName = message.getRoutingContext().getConsumerGroup() + "-delay";
            // 声明延迟队列（带TTL和死信设置）
            Queue delayQueue = QueueBuilder.durable(delayQueueName)
                    .withArgument("x-dead-letter-exchange", message.getRoutingContext().getBusinessDomain())
                    .withArgument("x-dead-letter-routing-key", message.getRoutingContext().getRoutingSelector())
                    .build();
            rabbitAdmin.declareQueue(delayQueue);

            // 声明Exchange
            DirectExchange exchange = new DirectExchange(delayExchangeName, true, false);
            rabbitAdmin.declareExchange(exchange);

            // 绑定延迟队列到延迟Exchange
            Binding delayBinding = BindingBuilder.bind(delayQueue)
                    .to(new TopicExchange(delayExchangeName))
                    .with(delayRoutingKey);
            rabbitAdmin.declareBinding(delayBinding);

            log.debug("Created delay queue: {} -> {} (delay: {}ms)",
                    delayQueueName, delayExchangeName, delayMs);

            return delayQueueName;

        } catch (Exception e) {
            log.error("Failed to create delay queue for routing key: {}", message.getRoutingContext().getRoutingSelector(), e);
            throw new RuntimeException("Failed to create delay queue", e);
        }
    }

    @Override
    public boolean isHealthy() {
        return healthy && connectionFactory != null;
    }

    @Override
    public Map<String, Object> buildSubscriptionProperties(Annotation config) {
        if (config instanceof RabbitConfig rabbitConfig) {
            Map<String, Object> properties = new HashMap<>();
            putProperty(properties, RabbitMQConstants.QUEUE_DURABLE, rabbitConfig.queueDurable());
            putProperty(properties, RabbitMQConstants.EXCHANGE_DURABLE, rabbitConfig.exchangeDurable());
            putProperty(properties, RabbitMQConstants.QUEUE_AUTO_DELETE, rabbitConfig.queueAutoDelete());
            putProperty(properties, RabbitMQConstants.EXCHANGE_AUTO_DELETE, rabbitConfig.exchangeAutoDelete());
            putProperty(properties,RabbitMQConstants.CONCURRENT_CONSUMERS, rabbitConfig.concurrentConsumers());

            putProperty(properties, RabbitMQConstants.X_MESSAGE_TTL, rabbitConfig.messageTTL());
            putProperty(properties, RabbitMQConstants.X_EXPIRES, rabbitConfig.expires());
            putProperty(properties, RabbitMQConstants.X_MAX_LENGTH, rabbitConfig.maxLength());
            putProperty(properties, RabbitMQConstants.X_MAX_LENGTH_BYTES, rabbitConfig.maxLengthBytes());
            QueueBuilder.Overflow overflow = convertOverflow(rabbitConfig.overflow());
            if (Objects.nonNull(overflow)) {
                putProperty(properties, RabbitMQConstants.X_OVERFLOW, rabbitConfig.overflow());
            }
            putProperty(properties, RabbitMQConstants.X_DEAD_LETTER_EXCHANGE, rabbitConfig.dlx());
            putProperty(properties, RabbitMQConstants.X_DEAD_LETTER_ROUTING_KEY, rabbitConfig.dlrk());
            putProperty(properties, RabbitMQConstants.X_MAX_PRIORITY, rabbitConfig.maxPriority());
            if (rabbitConfig.lazy()){
                putProperty(properties, RabbitMQConstants.X_QUEUE_MODE, "lazy");
            }
            QueueBuilder.LeaderLocator leaderLocator = convertLocator(rabbitConfig.locator());
            if (Objects.nonNull(leaderLocator)) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_MASTER_LOCATION, rabbitConfig.locator());
            }
            putProperty(properties, RabbitMQConstants.X_SINGLE_ACTIVE_CONSUMER, rabbitConfig.singleActiveConsumer());
            if (rabbitConfig.quorum()) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_TYPE, "quorum");
            }
            if (rabbitConfig.stream()) {
                putProperty(properties, RabbitMQConstants.X_QUEUE_TYPE, "stream");
            }
            putProperty(properties, RabbitMQConstants.X_DELIVERY_LIMIT, rabbitConfig.deliveryLimit());
            return properties;
        }
        return Collections.emptyMap();
    }

    private QueueBuilder.LeaderLocator convertLocator(String locator) {
        if (StringUtils.isBlank(locator)) {
            return null;
        }
        switch (locator) {
            case "min-masters":
                return QueueBuilder.LeaderLocator.minLeaders;
            case "client-local":
                return QueueBuilder.LeaderLocator.clientLocal;
            case "random":
                return QueueBuilder.LeaderLocator.random;
        }
        return null;
    }

    private QueueBuilder.Overflow convertOverflow(String overflow) {
        if (StringUtils.isBlank(overflow)) {
            return null;
        }
        switch (overflow) {
            case "drop-head":
                return QueueBuilder.Overflow.dropHead;
            case "reject-publish":
                return QueueBuilder.Overflow.rejectPublish;
        }
        return null;
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
    public Set<EventCapability> getSupportedCapabilities() {
        return Set.of(
                EventCapability.DELAYED_MESSAGE,
                EventCapability.TRANSACTIONAL_MESSAGE,
                EventCapability.BATCH_MESSAGE,
                EventCapability.PERSISTENT_MESSAGE,
                EventCapability.DEAD_LETTER_QUEUE,
                EventCapability.CLUSTER_SUPPORT

        );
    }

    /**
     * 设置消息属性
     */
    private void setMessageProperties(Message msg, TransportEvent message) {
        // 设置消息属性
        msg.getMessageProperties().setMessageId(message.getEventId());
        if (message.getPriority() != 0) {
            msg.getMessageProperties().setPriority(message.getPriority());
        }

        if (message.getTtl() != null) {
            msg.getMessageProperties().setExpiration(String.valueOf(message.getTtl()));
        }
    }

    /**
     * RabbitMQ 专用事件消息监听器
     * 实现 ChannelAwareMessageListener 以支持手动确认和完整的错误处理
     */
    private class RabbitMQEventMessageListener implements ChannelAwareMessageListener {

        private final MessageConsumer consumer;

        public RabbitMQEventMessageListener(MessageConsumer consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onMessage(Message message, Channel channel) throws Exception {
            String messageId = message.getMessageProperties().getMessageId();
            long deliveryTag = message.getMessageProperties().getDeliveryTag();

            TransportEvent transportEvent = buildTransportMessage(message);
            try {
                log.debug("Received message from RabbitMQ: {}", messageId);

                // 构建TransportMessage
                transportEvent.setEventStatus(EventStatus.SUCCESS);

                // 调用消费者处理消息
                ConsumeResult consume = consumer.consume(transportEvent);
                if (!ConsumeResult.SUCCESS.equals(consume)) {
                    throw new BusException("RabbitMQ transport consume failed");
                }

                // 手动确认消息
                channel.basicAck(deliveryTag, false);
                log.debug("Message acknowledged successfully: {}", messageId);

            } catch (Exception e) {
                log.error("Failed to process message: {}", messageId, e);

                // 处理消息失败
                handleMessageFailure(message, channel, deliveryTag, transportEvent.getRetryTimes(), e);
            }
        }

        /**
         * 构建传输消息对象
         */
        private TransportEvent buildTransportMessage(Message message) {
            return messageSerializer.deserialize(message.getBody(), TransportEvent.class);
        }

        /**
         * 处理消息失败
         */
        private void handleMessageFailure(Message message, Channel channel, long deliveryTag, Integer retryTimes, Exception e) {
            try {
                MessageProperties props = message.getMessageProperties();
                String messageId = props.getMessageId();

                // 获取重试次数
                Integer retryCount = getRetryCount(props);
                int maxRetries = Objects.nonNull(retryTimes) ? retryTimes : 3;

                if (retryCount < maxRetries) {
                    // 先确认原消息，避免重复处理
                    channel.basicAck(deliveryTag, false);

                    // 重新发送消息进行重试，并更新重试计数
                    retryMessageWithDelay(message, retryCount + 1, e);

                    log.warn("Message processing failed, will retry ({}/{}): {} - {}",
                            retryCount + 1, maxRetries, messageId, e.getMessage());
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
         * 重新发送消息进行重试，支持延迟和重试计数
         */
        private void retryMessageWithDelay(Message originalMessage, int retryCount, Exception lastException) {
            try {
                // 反序列化原始消息
                TransportEvent transportEvent = messageSerializer.deserialize(originalMessage.getBody(), TransportEvent.class);

                // 计算重试延迟时间（指数退避算法）
                long delayMs = calculateRetryDelay(retryCount);

                // 获取原始路由信息
                RoutingContext routingContext = transportEvent.getRoutingContext();
                String exchangeName = routingContext.getBusinessDomain();
                String routingKey = routingContext.getRoutingSelector();

                // 重新序列化消息
                byte[] messageBody = messageSerializer.serialize(transportEvent);

                if (delayMs > 0) {
                    // 使用延迟发送
                    sendDelayedRetryMessage(exchangeName, routingKey, messageBody, originalMessage, retryCount, lastException, delayMs);
                } else {
                    // 立即重试
                    sendImmediateRetryMessage(exchangeName, routingKey, messageBody, originalMessage, retryCount, lastException);
                }

                log.debug("Message requeued for retry with count {}, delay {}ms: {}",
                        retryCount, delayMs, transportEvent.getEventId());

            } catch (Exception retryException) {
                log.error("Failed to retry message, original exception: {}, retry exception: {}",
                        lastException.getMessage(), retryException.getMessage(), retryException);
            }
        }

        /**
         * 发送延迟重试消息
         */
        private void sendDelayedRetryMessage(String exchangeName, String routingKey, byte[] messageBody,
                                             Message originalMessage, int retryCount, Exception lastException, long delayMs) {
            // 创建延迟队列名称
            String delayQueueName = String.format("retry-delay-%s-%s-%d", exchangeName, routingKey, delayMs).replaceAll("[^a-zA-Z0-9\\-_.]", "-");

            try {
                // 确保延迟队列存在
                ensureDelayQueue(delayQueueName, exchangeName, routingKey, delayMs);

                // 发送到延迟队列
                rabbitTemplate.convertAndSend(
                        "", // 使用默认交换机直接发送到队列
                        delayQueueName,
                        messageBody,
                        msg -> {
                            copyOriginalMessageProperties(msg, originalMessage);
                            msg.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                            msg.getMessageProperties().getHeaders().put("x-last-exception", lastException.getMessage());
                            msg.getMessageProperties().getHeaders().put("x-retry-timestamp", System.currentTimeMillis());
                            return msg;
                        }
                );
            } catch (Exception e) {
                log.warn("Failed to send delayed retry message, falling back to immediate retry: {}", e.getMessage());
                // 降级为立即重试
                sendImmediateRetryMessage(exchangeName, routingKey, messageBody, originalMessage, retryCount, lastException);
            }
        }

        /**
         * 发送立即重试消息
         */
        private void sendImmediateRetryMessage(String exchangeName, String routingKey, byte[] messageBody,
                                               Message originalMessage, int retryCount, Exception lastException) {
            rabbitTemplate.convertAndSend(
                    exchangeName,
                    routingKey,
                    messageBody,
                    msg -> {
                        copyOriginalMessageProperties(msg, originalMessage);
                        msg.getMessageProperties().getHeaders().put("x-retry-count", retryCount);
                        msg.getMessageProperties().getHeaders().put("x-last-exception", lastException.getMessage());
                        msg.getMessageProperties().getHeaders().put("x-retry-timestamp", System.currentTimeMillis());
                        return msg;
                    }
            );
        }

        /**
         * 确保延迟队列存在
         */
        private void ensureDelayQueue(String delayQueueName, String targetExchange, String targetRoutingKey, long delayMs) {
            try {
                // 创建延迟队列，设置TTL和死信交换机
                Map<String, Object> queueArgs = new HashMap<>();
                queueArgs.put("x-message-ttl", delayMs);
                queueArgs.put("x-dead-letter-exchange", targetExchange);
                queueArgs.put("x-dead-letter-routing-key", targetRoutingKey);

                Queue delayQueue = QueueBuilder.durable(delayQueueName)
                        .withArguments(queueArgs)
                        .build();

                rabbitAdmin.declareQueue(delayQueue);

            } catch (Exception e) {
                log.warn("Failed to create delay queue: {}", delayQueueName, e);
                throw e;
            }
        }

        /**
         * 计算重试延迟时间（指数退避算法）
         */
        private long calculateRetryDelay(int retryCount) {
            // 基础延迟时间：1秒、2秒、4秒
            long baseDelayMs = 1000L;
            return baseDelayMs * (1L << (retryCount - 1)); // 2^(retryCount-1) * 1000ms
        }

        /**
         * 复制原始消息属性
         */
        private void copyOriginalMessageProperties(Message newMessage, Message originalMessage) {
            MessageProperties newProps = newMessage.getMessageProperties();
            MessageProperties originalProps = originalMessage.getMessageProperties();

            // 复制基本属性
            newProps.setMessageId(originalProps.getMessageId());
            newProps.setTimestamp(originalProps.getTimestamp());
            newProps.setContentType(originalProps.getContentType());
            newProps.setContentEncoding(originalProps.getContentEncoding());

            if (originalProps.getPriority() != null) {
                newProps.setPriority(originalProps.getPriority());
            }

            if (originalProps.getExpiration() != null) {
                newProps.setExpiration(originalProps.getExpiration());
            }

            // 复制用户自定义头部（除了重试相关的）
            if (originalProps.getHeaders() != null) {
                originalProps.getHeaders().forEach((key, value) -> {
                    if (!key.startsWith("x-retry") && !key.startsWith("x-first-death") && !key.startsWith("x-last-exception")) {
                        newProps.getHeaders().put(key, value);
                    }
                });
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