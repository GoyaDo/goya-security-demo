package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import com.ysmjjsy.goya.security.bus.transport.TransportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 事件传输实现
 * 使用 Redis Pub/Sub 机制实现事件传输
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
public class RedisEventTransport implements EventTransport {

    private static final Logger log = LoggerFactory.getLogger(RedisEventTransport.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final EventSerializer eventSerializer;
    private final RedisMessageListenerContainer messageListenerContainer;
    private final Map<String, Map<IEventListener<?>, MessageListener>> subscriptions;

    private volatile boolean started = false;

    public RedisEventTransport(RedisTemplate<String, String> redisTemplate,
                               EventSerializer eventSerializer,
                               RedisMessageListenerContainer messageListenerContainer) {
        this.redisTemplate = redisTemplate;
        this.eventSerializer = eventSerializer;
        this.messageListenerContainer = messageListenerContainer;
        this.subscriptions = new ConcurrentHashMap<>();
    }

    @Override
    public BusRemoteType getTransportType() {
        return BusRemoteType.REDIS;
    }

    @Override
    public CompletableFuture<TransportResult> send(IEvent<?> event) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                if (!check(event)) {
                    return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(),
                            new EventHandleException(event.getEventId(), "Event is local event"));
                }

                String serializedEvent = eventSerializer.serialize(event);
                redisTemplate.convertAndSend(event.getTopic(), serializedEvent);

                log.debug("Event sent to Redis topic '{}': {}", event.getTopic(), event.getEventId());
                return TransportResult.success(getTransportType(), event.getTopic(), event.getEventId());

            } catch (Exception e) {
                log.error("Failed to send event {} to Redis topic '{}'", event.getEventId(), event.getTopic(), e);
                return TransportResult.failure(getTransportType(), event.getTopic(), event.getEventId(), e);
            }
        });
    }

    @Override
    public <E extends IEvent<E>> void subscribe(String topic, IEventListener<E> listener, Class<E> eventType) {
        if (!started) {
            throw new IllegalStateException("Redis transport not started");
        }

        MessageListener messageListener = new RedisEventMessageListener<>(listener, eventType, eventSerializer, topic);

        subscriptions.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                .put(listener, messageListener);

        messageListenerContainer.addMessageListener(messageListener, new ChannelTopic(topic));

        log.info("Subscribed to Redis topic '{}' for event type {}", topic, eventType.getSimpleName());
    }

    @Override
    public void unsubscribe(String topic, IEventListener<?> listener) {
        Map<IEventListener<?>, MessageListener> topicSubscriptions = subscriptions.get(topic);
        if (topicSubscriptions != null) {
            MessageListener messageListener = topicSubscriptions.remove(listener);
            if (messageListener != null) {
                messageListenerContainer.removeMessageListener(messageListener, new ChannelTopic(topic));
                log.info("Unsubscribed from Redis topic '{}'", topic);
            }
        }
    }

    @Override
    public void start() {
        if (!started) {
            messageListenerContainer.start();
            started = true;
            log.info("Redis event transport started");
        }
    }

    @Override
    public void stop() {
        if (started) {
            messageListenerContainer.stop();
            subscriptions.clear();
            started = false;
            log.info("Redis event transport stopped");
        }
    }

    @Override
    public boolean supportsTransaction() {
        // Redis Pub/Sub 不支持事务
        return false;
    }

    @Override
    public boolean supportsOrdering() {
        // Redis Pub/Sub 不保证消息顺序
        return false;
    }

    /**
     * Redis 消息监听器实现
     * 直接实现MessageListener接口，避免MessageListenerAdapter的复杂性
     */
    private static class RedisEventMessageListener<E extends IEvent<E>> implements MessageListener {

        private static final Logger log = LoggerFactory.getLogger(RedisEventMessageListener.class);

        private final IEventListener<E> listener;
        private final Class<E> eventType;
        private final EventSerializer eventSerializer;
        private final String topic;

        public RedisEventMessageListener(IEventListener<E> listener, Class<E> eventType,
                                         EventSerializer eventSerializer, String topic) {
            this.listener = listener;
            this.eventType = eventType;
            this.eventSerializer = eventSerializer;
            this.topic = topic;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                // 将字节数组转换为字符串
                String messageBody = new String(message.getBody());

                log.debug("Received Redis message on topic '{}': {}", topic, messageBody);

                // 反序列化事件
                E event = eventSerializer.deserialize(messageBody, eventType);

                // 调用监听器处理事件
                listener.onEvent(event);

                log.debug("Successfully processed Redis event {} on topic '{}'",
                        event.getEventId(), topic);

            } catch (EventHandleException e) {
                log.error("Failed to handle Redis event on topic '{}': {}", topic, e.getMessage(), e);
            } catch (Exception e) {
                log.error("Failed to process Redis message on topic '{}': {}", topic, e.getMessage(), e);
            }
        }

        @Override
        public String toString() {
            return String.format("RedisEventMessageListener{topic='%s', eventType=%s, listener=%s}",
                    topic, eventType.getSimpleName(), listener.getClass().getSimpleName());
        }
    }
}