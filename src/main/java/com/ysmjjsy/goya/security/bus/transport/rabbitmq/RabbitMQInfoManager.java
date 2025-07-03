package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import cn.hutool.core.map.MapUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.rabbitmq.client.Channel;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.MessageTransportContext;
import com.ysmjjsy.goya.security.bus.core.AbstractListenerManage;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import com.ysmjjsy.goya.security.bus.exception.BusException;
import com.ysmjjsy.goya.security.bus.resolver.PropertyResolver;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategyManager;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ Exchange管理器
 * <p>
 * 负责动态创建和管理Exchange、Queue和Binding
 *
 * @author goya
 * @since 2025/6/26
 */
@Slf4j
public class RabbitMQInfoManager extends AbstractListenerManage {

    private final RabbitAdmin rabbitAdmin;
    private final MessageSerializer messageSerializer;
    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final Map<String, SimpleMessageListenerContainer> subscriptions = new ConcurrentHashMap<>();

    /**
     * Exchange缓存
     */
    private final Map<String, Exchange> exchangeCache = new ConcurrentHashMap<>();

    /**
     * Queue缓存
     */
    private final Map<String, Queue> queueCache = new ConcurrentHashMap<>();

    /**
     * Binding缓存
     */
    private final Map<String, Binding> bindingCache = new ConcurrentHashMap<>();

    public RabbitMQInfoManager(BusProperties properties,
                               RoutingStrategyManager routingStrategyManager,
                               RabbitAdmin rabbitAdmin,
                               MessageTransportContext messageTransportContext,
                               MessageSerializer messageSerializer,
                               RabbitTemplate rabbitTemplate,
                               ConnectionFactory connectionFactory) {
        super(properties, routingStrategyManager, messageTransportContext);
        this.rabbitAdmin = rabbitAdmin;
        this.messageSerializer = messageSerializer;
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void putCache(String name, Object object) {
        if (Objects.isNull(object)) {
            return;
        }

        if (object instanceof Exchange) {
            putCache((Exchange) object);
        }
        if (object instanceof Queue) {
            putCache((Queue) object);
        }
        if (object instanceof Binding) {
            putCache(name, (Binding) object);
        }
    }

    @Override
    public <T> T getCache(String name, Class<T> clazz) {
        if (clazz.equals(Exchange.class)) {
            return (T) exchangeCache.get(name);
        }
        if (clazz.equals(Queue.class)) {
            return (T) queueCache.get(name);
        }
        if (clazz.equals(Binding.class)) {
            return (T) bindingCache.get(name);
        }
        return null;
    }

    @Override
    public void createMqInfosAfter(SubscriptionConfig config) {
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

        Exchange exchange = ensureExchange(exchangeName, messageModel, exchangeDurable, exchangeAutoDelete);
        Queue queue = ensureQueue(queueName, queueDurable, queueAutoDelete, config.getProperties());
        createBinding(routingKey, exchange, queue);
        log.info("create mq infos after exchange {} and queue {}", exchangeName, queueName);
    }

    @Override
    public void subscribeToTransport(SubscriptionConfig config,
                                     IEventListener<? extends IEvent> listener,
                                     String eventKey,
                                     Class<? extends IEvent> event) {
        // 创建消息消费者包装器
        MessageConsumer consumer = new AbstractListenerManage.MessageConsumerWrapper(listener, eventKey, event);

        Integer concurrency = 1;
        if (MapUtil.isNotEmpty(config.getProperties())) {
            concurrency = (Integer) config.getProperties().get(RabbitMQConstants.CONCURRENT_CONSUMERS);
        }

        createListenerContainer(config.getRoutingContext().getBusinessDomain(),
                config.getRoutingContext().getConsumerGroup(),
                config.getRoutingContext().getRoutingSelector(),
                concurrency, consumer);
    }

    /**
     * 创建监听器容器
     */
    private void createListenerContainer(String exchangeName, String queueName, String routingKey,
                                         Integer concurrency, MessageConsumer consumer) {
        String subscriptionId = exchangeName + "_" + routingKey;
        try {

            // 创建消息监听器容器
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setQueueNames(queueName);
            container.setConcurrentConsumers(concurrency);

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
     * 将Exchange放入缓存
     */
    public void putCache(Exchange exchange) {
        exchangeCache.put(exchange.getName(), exchange);
    }

    /**
     * 将Queue放入缓存
     */
    public void putCache(Queue queue) {
        queueCache.put(queue.getName(), queue);
    }

    /**
     * 将Binding放入缓存
     */
    public void putCache(String routingKey, Binding binding) {
        bindingCache.put(routingKey, binding);
    }

    /**
     * 确保Exchange存在
     */
    public Exchange ensureExchange(String exchangeName, EventModel eventModel, boolean exchangeDurable, boolean exchangeAutoDelete) {
        // 从缓存获取
        Exchange exchange = exchangeCache.get(exchangeName);
        if (exchange != null) {
            return exchange;
        }

        // 创建Exchange
        exchange = createExchange(exchangeName, eventModel, exchangeDurable, exchangeAutoDelete);

        // 缓存
        exchangeCache.put(exchangeName, exchange);
        // 声明Exchange
        rabbitAdmin.declareExchange(exchange);
        log.info("Created and cached exchange: {} (type={})", exchangeName,
                exchange.getType());

        return exchange;
    }

    /**
     * 确保Queue存在
     */
    public Queue ensureQueue(String queueName, boolean queueDurable, boolean queueAutoDelete, Map<String, Object> properties) {
        // 从缓存获取
        Queue queue = queueCache.get(queueName);
        if (queue != null) {
            return queue;
        }

        // 创建Queue
        queue = createQueue(queueName, queueDurable, queueAutoDelete, properties);
        rabbitAdmin.declareQueue(queue);
        // 缓存
        queueCache.put(queueName, queue);

        log.info("Created and cached queue: {} (durable={})", queueName,
                queue.isDurable());

        return queue;
    }

    /**
     * 创建Exchange
     */
    private Exchange createExchange(String exchangeName, EventModel eventModel, boolean exchangeDurable, boolean exchangeAutoDelete) {
        Exchange exchange;

        switch (eventModel) {
            case QUEUE:
                exchange = new DirectExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
                break;

            case TOPIC:
                exchange = new TopicExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
                break;

            case BROADCAST:
                exchange = new FanoutExchange(exchangeName, exchangeDurable, exchangeAutoDelete);
                break;

            default:
                throw new IllegalArgumentException("Unsupported message model: " + eventModel);
        }

        return exchange;
    }

    /**
     * 创建Queue
     */
    private Queue createQueue(String queueName, boolean queueDurable, boolean queueAutoDelete, Map<String, Object> properties) {
        return new Queue(queueName, queueDurable, false, queueAutoDelete, properties);
    }

    /**
     * 创建Binding
     */
    private Binding createBinding(String routingKey, Exchange exchange, Queue queue) {
        if (Objects.isNull(exchange) || Objects.isNull(queue)) {
            throw new IllegalArgumentException("Exchange or Queue cannot be null");
        }

        // 从缓存获取
        Binding binding = bindingCache.get(routingKey);
        if (binding != null) {
            return binding;
        }

        // 绑定队列到Exchange
        binding = BindingBuilder.bind(queue)
                .to(exchange)
                .with(routingKey).noargs();
        rabbitAdmin.declareBinding(binding);
        putCache(routingKey, binding);
        return binding;
    }

    /**
     * 删除Exchange（谨慎使用）
     */
    public boolean deleteExchange(String exchangeName) {
        try {
            rabbitAdmin.deleteExchange(exchangeName);
            exchangeCache.remove(exchangeName);
            log.info("Deleted exchange: {}", exchangeName);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete exchange: {}", exchangeName, e);
            return false;
        }
    }

    /**
     * 删除Queue（谨慎使用）
     */
    public boolean deleteQueue(String queueName) {
        try {
            rabbitAdmin.deleteQueue(queueName);
            queueCache.remove(queueName);
            log.info("Deleted queue: {}", queueName);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete queue: {}", queueName, e);
            return false;
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        exchangeCache.clear();
        queueCache.clear();
        bindingCache.clear();
        log.info("Cleared all caches");
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

            if (consumer == null || consumer.getListener() == null) {
                // 拒绝消息并要求重新入队
                channel.basicNack(deliveryTag, false, true);
                return;
            }

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

            final String applicationName = PropertyResolver.getApplicationName(SpringUtil.getApplicationContext().getEnvironment());

            // 创建延迟队列名称
            String delayQueueName = String.format("%s-retry-delay-%s-%s-%d", applicationName, exchangeName, routingKey, delayMs).replaceAll("[^a-zA-Z0-9\\-_.]", "-");

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
