package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * RabbitMQ重试队列监听器
 * 处理延迟重试消息：
 * - 监听重试队列中的消息
 * - 将过期消息重新路由到原始队列
 * - 实现延迟重试机制
 *
 * @author goya
 * @since 2025/1/17
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitMQRetryQueueListener implements ChannelAwareMessageListener {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 监听重试队列，处理延迟重试消息
     */
    @RabbitListener(queues = "#{@rabbitMQEventTransport != null ? @rabbitMQEventTransport.getRetryQueueName() : ''}")
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            // 获取原始路由键
            String originalRoutingKey = (String) message.getMessageProperties()
                    .getHeaders().get("original-routing-key");
            
            if (originalRoutingKey != null) {
                // 获取原始交换器名称（从消息属性中获取或使用默认值）
                String originalExchange = (String) message.getMessageProperties()
                        .getHeaders().getOrDefault("original-exchange", "goya.events.topic");
                
                log.debug("Retrying message from retry queue to exchange '{}' with routing key '{}'", 
                        originalExchange, originalRoutingKey);
                
                // 重新发送到原始交换器和路由键
                rabbitTemplate.convertAndSend(originalExchange, originalRoutingKey, message);
                
                // 确认重试队列中的消息
                if (channel != null) {
                    channel.basicAck(deliveryTag, false);
                }
                
                log.debug("Successfully retried message to exchange '{}' with routing key '{}'", 
                        originalExchange, originalRoutingKey);
                
            } else {
                log.warn("Missing original-routing-key header in retry message, discarding message");
                
                // 拒绝消息，不重新入队
                if (channel != null) {
                    channel.basicNack(deliveryTag, false, false);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to process retry queue message: {}", e.getMessage(), e);
            
            // 拒绝消息，不重新入队（避免无限循环）
            if (channel != null) {
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
} 