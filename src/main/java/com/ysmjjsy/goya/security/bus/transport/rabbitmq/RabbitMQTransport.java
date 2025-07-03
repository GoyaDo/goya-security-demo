package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

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
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Set;

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
            // 处理延迟消息
            if (transportEvent.getDelayTime() != null) {
                return sendDelayedMessage(transportEvent);
            }

            // 解析消息模型
            EventModel eventModel = transportEvent.getEventModel();

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

        if (message.isPersistent()){
            msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        }

        if (message.getTtl() != null) {
            msg.getMessageProperties().setExpiration(String.valueOf(message.getTtl()));
        }
    }
} 