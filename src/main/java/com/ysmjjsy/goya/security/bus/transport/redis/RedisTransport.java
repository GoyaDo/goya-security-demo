package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import com.ysmjjsy.goya.security.bus.enums.EventCapability;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Redis传输层实现
 * <p>
 * 基于Redis的Pub/Sub机制实现消息传输
 * 支持：
 * - TOPIC模式：使用Redis Pub/Sub
 * - QUEUE模式：使用Redis List（BRPOP/LPUSH）
 * - BROADCAST模式：使用Redis Pub/Sub广播
 * - 延迟消息：使用Redis ZSET实现
 *
 * @author goya
 * @since 2025/7/1 15:07
 */
@Slf4j
public class RedisTransport implements MessageTransport {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final MessageSerializer messageSerializer;
    private final Map<String, RedisMessageListenerContainer> subscriptions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService delayedMessageExecutor;
    private volatile boolean healthy = true;

    // Redis键前缀
    private static final String TOPIC_PREFIX = "mq:topic:";
    private static final String QUEUE_PREFIX = "mq:queue:";
    private static final String DELAYED_PREFIX = "mq:delayed:";
    private static final String DEAD_LETTER_PREFIX = "mq:dlq:";

    public RedisTransport(RedisTemplate<String, String> redisTemplate,
                          RedisConnectionFactory connectionFactory,
                          MessageSerializer messageSerializer) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.messageSerializer = messageSerializer;
        this.delayedMessageExecutor = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r, "redis-delayed-message-processor");
            t.setDaemon(true);
            return t;
        });

        // 启动延迟消息处理器
//        startDelayedMessageProcessor();
        checkHealth();
        log.info("Redis transport initialized with Pub/Sub and List support");
    }

    @Override
    public TransportResult send(TransportEvent transportEvent) {
        try {
            // 处理延迟消息
            if (transportEvent.getDelayTime() != null) {
                return sendDelayedMessage(transportEvent);
            }

            // 根据消息模型选择发送策略
            EventModel eventModel = transportEvent.getEventModel();
            switch (eventModel) {
                case TOPIC:
                case BROADCAST:
                    return sendToTopic(transportEvent);
                case QUEUE:
                default:
                    return sendToQueue(transportEvent);
            }

        } catch (Exception e) {
            log.error("Failed to send message via Redis: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到Redis Pub/Sub主题
     */
    private TransportResult sendToTopic(TransportEvent transportEvent) {
        try {
            RoutingContext routingContext = transportEvent.getRoutingContext();
            String topic = buildTopicName(routingContext);

            // 序列化消息
            byte[] messageData = messageSerializer.serialize(transportEvent);
            String messageString = java.util.Base64.getEncoder().encodeToString(messageData);

            // 发布到Redis主题
            redisTemplate.convertAndSend(topic, messageString);

            log.debug("Message sent to Redis topic: {}, messageId: {}", topic, transportEvent.getEventId());
            return TransportResult.success(transportEvent.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to Redis topic: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送到Redis队列
     */
    private TransportResult sendToQueue(TransportEvent transportEvent) {
        try {
            RoutingContext routingContext = transportEvent.getRoutingContext();
            String queueName = buildQueueName(routingContext);

            // 序列化消息
            byte[] messageData = messageSerializer.serialize(transportEvent);
            String messageString = java.util.Base64.getEncoder().encodeToString(messageData);

            // 推送到Redis列表
            redisTemplate.opsForList().leftPush(queueName, messageString);

            log.debug("Message sent to Redis queue: {}, messageId: {}", queueName, transportEvent.getEventId());
            return TransportResult.success(transportEvent.getEventId());

        } catch (Exception e) {
            log.error("Failed to send message to Redis queue: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 发送延迟消息
     */
    private TransportResult sendDelayedMessage(TransportEvent transportEvent) {
        try {
            long executeTime = System.currentTimeMillis() + transportEvent.getDelayTime().toMillis();
            String delayedKey = DELAYED_PREFIX + transportEvent.getRoutingContext().getBusinessDomain();

            // 序列化消息
            byte[] messageData = messageSerializer.serialize(transportEvent);
            String messageString = java.util.Base64.getEncoder().encodeToString(messageData);

            // 添加到延迟队列（使用ZSET，score为执行时间）
            redisTemplate.opsForZSet().add(delayedKey, messageString, executeTime);

            log.debug("Delayed message scheduled: executeTime={}, messageId={}", 
                     executeTime, transportEvent.getEventId());
            return TransportResult.success(transportEvent.getEventId());

        } catch (Exception e) {
            log.error("Failed to send delayed message: {}", transportEvent.getEventId(), e);
            return TransportResult.failure(e.getMessage(), e);
        }
    }

    @Override
    public void subscribe(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            EventModel eventModel = config.getEventModel();
            
            switch (eventModel) {
                case TOPIC:
                case BROADCAST:
                    subscribeToTopic(config, consumer);
                    break;
                case QUEUE:
                default:
                    subscribeToQueue(config, consumer);
                    break;
            }

        } catch (Exception e) {
            log.error("Failed to subscribe to Redis with config: {}", config, e);
            throw new RuntimeException("Failed to subscribe to Redis", e);
        }
    }

    /**
     * 订阅Redis Pub/Sub主题
     */
    private void subscribeToTopic(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            String topic = buildTopicName(config.getRoutingContext());
            String subscriptionId = topic + "_" + config.getRoutingContext().getConsumerGroup();

            // 创建消息监听容器
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);

            // 创建消息监听器
            MessageListenerAdapter listener = new MessageListenerAdapter(
                new RedisTopicMessageListener(consumer, messageSerializer), "onMessage");

            // 添加监听器和主题
            container.addMessageListener(listener, new PatternTopic(topic));
            container.start();

            // 保存订阅信息
            subscriptions.put(subscriptionId, container);

            log.info("Subscribed to Redis topic: {}", topic);

        } catch (Exception e) {
            log.error("Failed to subscribe to Redis topic", e);
            throw new RuntimeException("Failed to subscribe to Redis topic", e);
        }
    }

    /**
     * 订阅Redis队列
     */
    private void subscribeToQueue(SubscriptionConfig config, MessageConsumer consumer) {
        try {
            String queueName = buildQueueName(config.getRoutingContext());
            String subscriptionId = queueName + "_consumer";

            // 启动队列消费者线程
            Thread consumerThread = new Thread(() -> {
                log.info("Started Redis queue consumer for: {}", queueName);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // 阻塞式获取消息（BRPOP）
                        String message = redisTemplate.opsForList().rightPop(queueName, Duration.ofSeconds(5));
                        if (message != null) {
                            processQueueMessage(message, consumer);
                        }
                    } catch (Exception e) {
                        log.error("Error processing queue message from {}", queueName, e);
                        try {
                            Thread.sleep(1000); // 错误后等待1秒
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                log.info("Redis queue consumer stopped for: {}", queueName);
            }, "redis-queue-consumer-" + queueName);

            consumerThread.setDaemon(true);
            consumerThread.start();

            log.info("Subscribed to Redis queue: {}", queueName);

        } catch (Exception e) {
            log.error("Failed to subscribe to Redis queue", e);
            throw new RuntimeException("Failed to subscribe to Redis queue", e);
        }
    }

    /**
     * 处理队列消息
     */
    private void processQueueMessage(String message, MessageConsumer consumer) {
        try {
            // 反序列化消息
            byte[] messageData = java.util.Base64.getDecoder().decode(message);
            TransportEvent transportEvent = messageSerializer.deserialize(messageData, TransportEvent.class);

            // 调用消费者处理消息
            ConsumeResult result = consumer.consume(transportEvent);
            
            if (result != ConsumeResult.SUCCESS) {
                log.warn("Message consumption failed, messageId: {}", transportEvent.getEventId());
                // 可以实现重试逻辑或死信队列
            } else {
                log.debug("Message consumed successfully, messageId: {}", transportEvent.getEventId());
            }

        } catch (Exception e) {
            log.error("Failed to process queue message", e);
        }
    }

    /**
     * 启动延迟消息处理器
     */
    private void startDelayedMessageProcessor() {
        delayedMessageExecutor.scheduleWithFixedDelay(() -> {
            try {
                processDelayedMessages();
            } catch (Exception e) {
                log.error("Error processing delayed messages", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 处理延迟消息
     */
    private void processDelayedMessages() {
        // 获取所有延迟消息键
        Set<String> delayedKeys = redisTemplate.keys(DELAYED_PREFIX + "*");
        
        if (delayedKeys == null || delayedKeys.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        
        for (String delayedKey : delayedKeys) {
            try {
                // 获取到期的消息
                Set<String> expiredMessages = redisTemplate.opsForZSet()
                    .rangeByScore(delayedKey, 0, currentTime);
                
                if (expiredMessages != null && !expiredMessages.isEmpty()) {
                    for (String message : expiredMessages) {
                        // 移除已处理的延迟消息
                        redisTemplate.opsForZSet().remove(delayedKey, message);
                        
                        // 重新发送消息
                        redeliverDelayedMessage(message);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing delayed messages from key: {}", delayedKey, e);
            }
        }
    }

    /**
     * 重新投递延迟消息
     */
    private void redeliverDelayedMessage(String message) {
        try {
            // 反序列化消息
            byte[] messageData = java.util.Base64.getDecoder().decode(message);
            TransportEvent transportEvent = messageSerializer.deserialize(messageData, TransportEvent.class);
            
            // 清除延迟时间，重新发送
            transportEvent.setDelayTime(null);
            send(transportEvent);
            
            log.debug("Redelivered delayed message: {}", transportEvent.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to redeliver delayed message", e);
        }
    }

    @Override
    public TransportType getTransportType() {
        return TransportType.REDIS;
    }

    @Override
    public Set<EventCapability> getSupportedCapabilities() {
        return Set.of(
                EventCapability.DELAYED_MESSAGE,
                EventCapability.PERSISTENT_MESSAGE,
                EventCapability.CLUSTER_SUPPORT
        );
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    /**
     * 构建主题名称
     */
    private String buildTopicName(RoutingContext routingContext) {
        return TOPIC_PREFIX + routingContext.getBusinessDomain() + ":" + routingContext.getRoutingSelector();
    }

    /**
     * 构建队列名称
     */
    private String buildQueueName(RoutingContext routingContext) {
        return QUEUE_PREFIX + routingContext.getBusinessDomain() + ":" + routingContext.getConsumerGroup();
    }

    /**
     * 健康检查
     */
    private void checkHealth() {
        try {
            redisTemplate.opsForValue().get("health_check");
            healthy = true;
            log.debug("Redis transport health check passed");
        } catch (Exception e) {
            healthy = false;
            log.warn("Redis transport health check failed", e);
        }
    }

    /**
     * Redis主题消息监听器
     */
    public static class RedisTopicMessageListener {
        private final MessageConsumer consumer;
        private final MessageSerializer messageSerializer;

        public RedisTopicMessageListener(MessageConsumer consumer, MessageSerializer messageSerializer) {
            this.consumer = consumer;
            this.messageSerializer = messageSerializer;
        }

        public void onMessage(String message) {
            try {
                // 反序列化消息
                byte[] messageData = java.util.Base64.getDecoder().decode(message);
                TransportEvent transportEvent = messageSerializer.deserialize(messageData, TransportEvent.class);

                // 调用消费者处理消息
                ConsumeResult result = consumer.consume(transportEvent);
                
                if (result != ConsumeResult.SUCCESS) {
                    log.warn("Message consumption failed, messageId: {}", transportEvent.getEventId());
                } else {
                    log.debug("Message consumed successfully, messageId: {}", transportEvent.getEventId());
                }

            } catch (Exception e) {
                log.error("Failed to process topic message", e);
            }
        }
    }
}
