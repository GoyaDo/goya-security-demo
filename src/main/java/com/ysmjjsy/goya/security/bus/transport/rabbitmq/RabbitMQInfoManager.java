package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import cn.hutool.core.map.MapUtil;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.Map;
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
@RequiredArgsConstructor
public class RabbitMQInfoManager {

    private final RabbitAdmin rabbitAdmin;

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

    /**
     * 确保Exchange存在
     */
    public Exchange ensureExchange(SubscriptionConfig config) {
        RoutingContext context = config.getRoutingContext();
        String exchangeName = context.getBusinessDomain();

        // 从缓存获取
        Exchange exchange = exchangeCache.get(exchangeName);
        if (exchange != null) {
            return exchange;
        }

        // 创建Exchange
        exchange = createExchange(config);

        // 声明Exchange
        rabbitAdmin.declareExchange(exchange);

        // 缓存
        exchangeCache.put(exchangeName, exchange);

        log.info("Created and cached exchange: {} (type={})", exchangeName,
                exchange.getType());

        return exchange;
    }

    /**
     * 确保Queue存在
     */
    public Queue ensureQueue(SubscriptionConfig config) {
        RoutingContext context = config.getRoutingContext();
        String queueName = context.getConsumerGroup();

        // 从缓存获取
        Queue queue = queueCache.get(queueName);
        if (queue != null) {
            return queue;
        }

        // 创建Queue
        queue = createQueue(config);

        // 声明Queue
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
    private Exchange createExchange(SubscriptionConfig config) {
        RoutingContext context = config.getRoutingContext();
        String exchangeName = context.getBusinessDomain();
        EventModel eventModel = context.getEventModel();
        Map<String, Object> properties = config.getProperties();
        boolean exchangeDurable = true;
        boolean exchangeAutoDelete = true;


        if (MapUtil.isNotEmpty(properties)) {
            exchangeDurable = (boolean) properties.get(RabbitMQConstants.EXCHANGE_DURABLE);
            exchangeAutoDelete = (boolean) properties.get(RabbitMQConstants.EXCHANGE_AUTO_DELETE);
        }

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
    private Queue createQueue(SubscriptionConfig config) {
        RoutingContext context = config.getRoutingContext();
        String queueName = context.getConsumerGroup();
        Map<String, Object> properties = config.getProperties();
        boolean queueDurable = true;
        boolean queueAutoDelete = true;


        if (MapUtil.isNotEmpty(properties)) {
            queueDurable = (boolean) properties.get(RabbitMQConstants.QUEUE_DURABLE);
            queueAutoDelete = (boolean) properties.get(RabbitMQConstants.QUEUE_AUTO_DELETE);
        }
        return new Queue(queueName, queueDurable, false, queueAutoDelete, properties);
    }

    /**
     * 创建Binding
     */
    private Binding createBinding(RoutingContext context, Exchange exchange, Queue queue) {
        String routingKey = context.getRoutingSelector();
        EventModel eventModel = context.getEventModel();

        Binding binding;

        switch (eventModel) {
            case QUEUE:
                binding = BindingBuilder.bind(queue)
                        .to((DirectExchange) exchange)
                        .with(routingKey);
                break;

            case TOPIC:
                binding = BindingBuilder.bind(queue)
                        .to((TopicExchange) exchange)
                        .with(routingKey);
                break;

            case BROADCAST:
                binding = BindingBuilder.bind(queue)
                        .to((FanoutExchange) exchange);
                break;

            default:
                throw new IllegalArgumentException("Unsupported message model: " + eventModel);
        }

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

}
