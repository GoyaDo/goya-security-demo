//package com.ysmjjsy.goya.security.bus.transport.rabbitmq;
//
//import com.ysmjjsy.goya.security.bus.enums.MessageModel;
//import com.ysmjjsy.goya.security.bus.route.RoutingContext;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.core.*;
//import org.springframework.amqp.rabbit.core.RabbitAdmin;
//import org.springframework.stereotype.Component;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
///**
// * RabbitMQ Exchange管理器
// *
// * 负责动态创建和管理Exchange、Queue和Binding
// *
// * @author goya
// * @since 2025/6/26
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class RabbitMQExchangeManager {
//
//    private final RabbitAdmin rabbitAdmin;
//    private final RoutingConfigurationProperties routingConfig;
//
//    /**
//     * Exchange缓存
//     */
//    private final Map<String, Exchange> exchangeCache = new ConcurrentHashMap<>();
//
//    /**
//     * Queue缓存
//     */
//    private final Map<String, Queue> queueCache = new ConcurrentHashMap<>();
//
//    /**
//     * Binding缓存
//     */
//    private final Map<String, Binding> bindingCache = new ConcurrentHashMap<>();
//
//    /**
//     * 确保Exchange存在
//     */
//    public Exchange ensureExchange(RoutingContext context) {
//        String exchangeName = context.getRabbitMQExchangeName();
//
//        // 从缓存获取
//        Exchange exchange = exchangeCache.get(exchangeName);
//        if (exchange != null) {
//            return exchange;
//        }
//
//        // 创建Exchange
//        exchange = createExchange(context);
//
//        // 声明Exchange
//        rabbitAdmin.declareExchange(exchange);
//
//        // 缓存
//        exchangeCache.put(exchangeName, exchange);
//
//        log.info("Created and cached exchange: {} (type={})", exchangeName,
//                exchange.getType());
//
//        return exchange;
//    }
//
//    /**
//     * 确保Queue存在
//     */
//    public Queue ensureQueue(RoutingContext context) {
//        String queueName = context.getRabbitMQQueueName();
//
//        // 从缓存获取
//        Queue queue = queueCache.get(queueName);
//        if (queue != null) {
//            return queue;
//        }
//
//        // 创建Queue
//        queue = createQueue(context);
//
//        // 声明Queue
//        rabbitAdmin.declareQueue(queue);
//
//        // 缓存
//        queueCache.put(queueName, queue);
//
//        log.info("Created and cached queue: {} (durable={})", queueName,
//                queue.isDurable());
//
//        return queue;
//    }
//
//    /**
//     * 确保Binding存在
//     */
//    public Binding ensureBinding(RoutingContext context) {
//        String bindingKey = buildBindingKey(context);
//
//        // 从缓存获取
//        Binding binding = bindingCache.get(bindingKey);
//        if (binding != null) {
//            return binding;
//        }
//
//        // 确保Exchange和Queue存在
//        Exchange exchange = ensureExchange(context);
//        Queue queue = ensureQueue(context);
//
//        // 创建Binding
//        binding = createBinding(context, exchange, queue);
//
//        // 声明Binding
//        rabbitAdmin.declareBinding(binding);
//
//        // 缓存
//        bindingCache.put(bindingKey, binding);
//
//        log.info("Created and cached binding: exchange={}, queue={}, routingKey={}",
//                exchange.getName(), queue.getName(), context.getRabbitMQRoutingKey());
//
//        return binding;
//    }
//
//    /**
//     * 创建Exchange
//     */
//    private Exchange createExchange(RoutingContext context) {
//        String exchangeName = context.getRabbitMQExchangeName();
//        MessageModel messageModel = context.getMessageModel();
//
//        // 获取业务域配置
//        RoutingConfigurationProperties.BusinessDomainConfig domainConfig =
//                routingConfig.getBusinessDomainConfig(context.getEventType());
//
//        boolean durable = domainConfig.isDurable();
//        boolean autoDelete = domainConfig.isAutoDelete();
//        Map<String, Object> arguments = new HashMap<>(routingConfig.getRabbitmq().getExchangeArguments());
//
//        // 添加自定义属性
//        if (domainConfig.getCustomProperties() != null) {
//            arguments.putAll(domainConfig.getCustomProperties());
//        }
//
//        Exchange exchange;
//
//        switch (messageModel) {
//            case QUEUE:
//                exchange = new DirectExchange(exchangeName, durable, autoDelete, arguments);
//                break;
//
//            case TOPIC:
//                exchange = new TopicExchange(exchangeName, durable, autoDelete, arguments);
//                break;
//
//            case BROADCAST:
//                exchange = new FanoutExchange(exchangeName, durable, autoDelete, arguments);
//                break;
//
//            default:
//                throw new IllegalArgumentException("Unsupported message model: " + messageModel);
//        }
//
//        return exchange;
//    }
//
//    /**
//     * 创建Queue
//     */
//    private Queue createQueue(RoutingContext context) {
//        String queueName = context.getRabbitMQQueueName();
//
//        // 获取业务域配置
//        RoutingConfigurationProperties.BusinessDomainConfig domainConfig =
//                routingConfig.getBusinessDomainConfig(context.getEventType());
//
//        boolean durable = domainConfig.isDurable();
//        boolean exclusive = false; // 通常不使用排他队列
//        boolean autoDelete = domainConfig.isAutoDelete();
//        Map<String, Object> arguments = new HashMap<>(routingConfig.getRabbitmq().getQueueArguments());
//
//        // 设置TTL
//        if (domainConfig.getTtl() != null) {
//            arguments.put("x-message-ttl", domainConfig.getTtl());
//        }
//
//        // 添加自定义属性
//        if (domainConfig.getCustomProperties() != null) {
//            arguments.putAll(domainConfig.getCustomProperties());
//        }
//
//        return new Queue(queueName, durable, exclusive, autoDelete, arguments);
//    }
//
//    /**
//     * 创建Binding
//     */
//    private Binding createBinding(RoutingContext context, Exchange exchange, Queue queue) {
//        String routingKey = context.getRabbitMQRoutingKey();
//        MessageModel messageModel = context.getMessageModel();
//
//        Binding binding;
//
//        switch (messageModel) {
//            case QUEUE:
//                binding = BindingBuilder.bind(queue)
//                        .to((DirectExchange) exchange)
//                        .with(routingKey);
//                break;
//
//            case TOPIC:
//                binding = BindingBuilder.bind(queue)
//                        .to((TopicExchange) exchange)
//                        .with(routingKey);
//                break;
//
//            case BROADCAST:
//                binding = BindingBuilder.bind(queue)
//                        .to((FanoutExchange) exchange);
//                break;
//
//            default:
//                throw new IllegalArgumentException("Unsupported message model: " + messageModel);
//        }
//
//        return binding;
//    }
//
//    /**
//     * 构建Binding缓存键
//     */
//    private String buildBindingKey(RoutingContext context) {
//        return context.getRabbitMQExchangeName() + "|" +
//               context.getRabbitMQQueueName() + "|" +
//               context.getRabbitMQRoutingKey();
//    }
//
//    /**
//     * 创建延迟Exchange
//     */
//    public Exchange ensureDelayExchange(String delayExchangeName) {
//        // 从缓存获取
//        Exchange exchange = exchangeCache.get(delayExchangeName);
//        if (exchange != null) {
//            return exchange;
//        }
//
//        // 检查是否启用延迟Exchange
//        if (!routingConfig.getRabbitmq().isEnableDelayExchange()) {
//            throw new UnsupportedOperationException("Delay exchange is not enabled");
//        }
//
//        // 创建延迟Exchange（需要x-delayed-message插件）
//        Map<String, Object> arguments = new HashMap<>();
//        arguments.put("x-delayed-type", "topic"); // 内部使用topic类型
//
//        exchange = new CustomExchange(
//                delayExchangeName,
//                routingConfig.getRabbitmq().getDelayExchangeType(),
//                routingConfig.getRabbitmq().isDefaultExchangeDurable(),
//                routingConfig.getRabbitmq().isDefaultExchangeAutoDelete(),
//                arguments
//        );
//
//        // 声明Exchange
//        rabbitAdmin.declareExchange(exchange);
//
//        // 缓存
//        exchangeCache.put(delayExchangeName, exchange);
//
//        log.info("Created delay exchange: {} (type={})", delayExchangeName,
//                routingConfig.getRabbitmq().getDelayExchangeType());
//
//        return exchange;
//    }
//
//    /**
//     * 删除Exchange（谨慎使用）
//     */
//    public boolean deleteExchange(String exchangeName) {
//        try {
//            rabbitAdmin.deleteExchange(exchangeName);
//            exchangeCache.remove(exchangeName);
//            log.info("Deleted exchange: {}", exchangeName);
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to delete exchange: {}", exchangeName, e);
//            return false;
//        }
//    }
//
//    /**
//     * 删除Queue（谨慎使用）
//     */
//    public boolean deleteQueue(String queueName) {
//        try {
//            rabbitAdmin.deleteQueue(queueName);
//            queueCache.remove(queueName);
//            log.info("Deleted queue: {}", queueName);
//            return true;
//        } catch (Exception e) {
//            log.error("Failed to delete queue: {}", queueName, e);
//            return false;
//        }
//    }
//
//    /**
//     * 清除缓存
//     */
//    public void clearCache() {
//        exchangeCache.clear();
//        queueCache.clear();
//        bindingCache.clear();
//        log.info("Cleared all caches");
//    }
//
//    /**
//     * 获取管理统计信息
//     */
//    public Map<String, Object> getStatistics() {
//        Map<String, Object> stats = new HashMap<>();
//        stats.put("exchangeCount", exchangeCache.size());
//        stats.put("queueCount", queueCache.size());
//        stats.put("bindingCount", bindingCache.size());
//        stats.put("exchanges", exchangeCache.keySet());
//        stats.put("queues", queueCache.keySet());
//        return stats;
//    }
//}
//