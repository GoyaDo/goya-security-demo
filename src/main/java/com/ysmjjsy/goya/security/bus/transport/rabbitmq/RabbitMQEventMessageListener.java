package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.rabbitmq.client.Channel;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import com.ysmjjsy.goya.security.bus.serializer.SerializationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * RabbitMQ 事件消息监听器
 * 实现 ChannelAwareMessageListener 接口，支持手动消息确认
 * 
 * 功能特性：
 * - 手动消息确认机制
 * - 自动重试机制
 * - 错误分类处理
 * - 死信队列支持
 * - 消息去重和幂等性
 *
 * @author goya
 * @since 2025/1/17
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMQEventMessageListener<T extends IEvent> implements ChannelAwareMessageListener {

    private final IEventListener<T> listener;
    private final Class<T> eventType;
    private final EventSerializer eventSerializer;
    private final String topic;
    private final RabbitMQEventTransport transport;

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        Integer retryCount = (Integer) message.getMessageProperties().getHeaders().getOrDefault("retry-count", 0);
        
        try {
            // 获取消息内容
            String messageBody = new String(message.getBody());
            
            log.debug("Received RabbitMQ message on topic '{}' (attempt {}): messageId={}, body={}",
                    topic, retryCount + 1, messageId, messageBody);

            // 反序列化事件
            T event = deserializeEvent(messageBody);

            // 调用监听器处理事件
            processEvent(event);

            // 手动确认消息
            acknowledgeMessage(channel, deliveryTag);

            log.debug("Successfully processed RabbitMQ event {} on topic '{}' (attempt {})",
                    event.getEventId(), topic, retryCount + 1);

        } catch (SerializationException e) {
            log.error("Failed to deserialize message on topic '{}' (messageId={}): {}", 
                    topic, messageId, e.getMessage(), e);
            // 序列化错误不重试，直接拒绝
            rejectMessage(channel, deliveryTag, false);
            
        } catch (EventHandleException e) {
            log.error("Business logic error handling event on topic '{}' (messageId={}): {}", 
                    topic, messageId, e.getMessage(), e);
            // 业务逻辑错误，判断是否应该重试
            handleBusinessException(message, channel, deliveryTag, e);
            
        } catch (Exception e) {
            log.error("System error processing message on topic '{}' (messageId={}): {}", 
                    topic, messageId, e.getMessage(), e);
            // 系统错误，进行重试
            handleSystemException(message, channel, deliveryTag, e);
        }
    }

    /**
     * 反序列化事件
     */
    private T deserializeEvent(String messageBody) throws SerializationException {
        try {
            return eventSerializer.deserialize(messageBody, eventType);
        } catch (Exception e) {
            throw new SerializationException("Failed to deserialize event: " + e.getMessage(), e);
        }
    }

    /**
     * 处理事件
     */
    private void processEvent(T event) throws Exception {
        listener.onEvent(event);
    }

    /**
     * 确认消息
     */
    private void acknowledgeMessage(Channel channel, long deliveryTag) throws Exception {
        if (channel != null) {
            channel.basicAck(deliveryTag, false);
            log.trace("Message acknowledged: deliveryTag={}", deliveryTag);
        }
    }

    /**
     * 拒绝消息
     */
    private void rejectMessage(Channel channel, long deliveryTag, boolean requeue) throws Exception {
        if (channel != null) {
            channel.basicNack(deliveryTag, false, requeue);
            log.warn("Message rejected: deliveryTag={}, requeue={}", deliveryTag, requeue);
        }
    }

    /**
     * 处理业务异常
     */
    private void handleBusinessException(Message message, Channel channel, long deliveryTag, EventHandleException e) 
            throws Exception {
        // 判断业务异常是否应该重试
        if (shouldRetryBusinessException(e)) {
            handleRetryableException(message, channel, deliveryTag, e);
        } else {
            // 不重试的业务异常，直接拒绝
            rejectMessage(channel, deliveryTag, false);
            // 可选：发送到死信队列
            transport.handleRetry(message, topic, e);
        }
    }

    /**
     * 处理系统异常
     */
    private void handleSystemException(Message message, Channel channel, long deliveryTag, Exception e) 
            throws Exception {
        // 系统异常通常需要重试
        handleRetryableException(message, channel, deliveryTag, e);
    }

    /**
     * 处理可重试异常
     */
    private void handleRetryableException(Message message, Channel channel, long deliveryTag, Exception e) 
            throws Exception {
        // 拒绝消息但不重新入队（由重试机制处理）
        rejectMessage(channel, deliveryTag, false);
        
        // 通过传输层处理重试
        transport.handleRetry(message, topic, e);
    }

    /**
     * 判断业务异常是否应该重试
     */
    private boolean shouldRetryBusinessException(EventHandleException e) {
        // 根据异常类型和错误码判断是否应该重试
        // 这里可以根据具体业务需求进行定制
        
        // 示例逻辑：
        // - 如果是临时性业务错误（如外部服务暂时不可用），可以重试
        // - 如果是永久性业务错误（如数据验证失败），不应该重试
        
        String errorMessage = e.getMessage();
        if (errorMessage != null) {
            // 网络相关错误，可以重试
            if (errorMessage.contains("timeout") || 
                errorMessage.contains("connection") || 
                errorMessage.contains("network")) {
                return true;
            }
            
            // 数据验证错误，不重试
            if (errorMessage.contains("validation") || 
                errorMessage.contains("invalid") || 
                errorMessage.contains("illegal")) {
                return false;
            }
        }
        
        // 默认不重试业务异常
        return false;
    }

    @Override
    public String toString() {
        return String.format("RabbitMQEventMessageListener{topic='%s', eventType=%s, listener=%s}",
                topic, eventType.getSimpleName(), listener.getClass().getSimpleName());
    }
} 