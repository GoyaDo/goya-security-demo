package com.ysmjjsy.goya.security.bus.transport;

import com.rabbitmq.client.Channel;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.listener.IEventListener;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * RabbitMQ 事件消息监听器
 * 实现 ChannelAwareMessageListener 接口，支持手动消息确认
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

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            // 获取消息内容
            String messageBody = new String(message.getBody());
            
            log.debug("Received RabbitMQ message on topic '{}': {}", topic, messageBody);

            // 反序列化事件
            T event = eventSerializer.deserialize(messageBody, eventType);

            // 调用监听器处理事件
            listener.onEvent(event);

            // 手动确认消息
            if (channel != null) {
                channel.basicAck(deliveryTag, false);
            }

            log.debug("Successfully processed RabbitMQ event {} on topic '{}'",
                    event.getEventId(), topic);

        } catch (EventHandleException e) {
            log.error("Failed to handle RabbitMQ event on topic '{}': {}", topic, e.getMessage(), e);
            handleEventProcessingError(channel, deliveryTag, e);
        } catch (Exception e) {
            log.error("Failed to process RabbitMQ message on topic '{}': {}", topic, e.getMessage(), e);
            handleEventProcessingError(channel, deliveryTag, e);
        }
    }

    /**
     * 处理事件处理错误
     */
    private void handleEventProcessingError(Channel channel, long deliveryTag, Exception e) throws Exception {
        if (channel != null) {
            // 判断是否应该重新入队
            boolean requeue = shouldRequeue(e);
            
            // 拒绝消息
            channel.basicNack(deliveryTag, false, requeue);
            
            log.warn("Message rejected, requeue: {}, error: {}", requeue, e.getMessage());
        }
    }

    /**
     * 判断是否应该重新入队
     */
    private boolean shouldRequeue(Exception e) {
        // 对于业务异常，不重新入队
        if (e instanceof EventHandleException) {
            return false;
        }
        
        // 对于系统异常，可以考虑重新入队
        // 这里可以根据具体需求进行调整
        return true;
    }

    @Override
    public String toString() {
        return String.format("RabbitMQEventMessageListener{topic='%s', eventType=%s, listener=%s}",
                topic, eventType.getSimpleName(), listener.getClass().getSimpleName());
    }
} 