package com.ysmjjsy.goya.security.bus.transport.rabbitmq;

import cn.hutool.extra.spring.SpringUtil;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.PropertyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>延迟队列管理器</p>
 * <p>负责管理RabbitMQ延迟队列的创建、配置和清理</p>
 *
 * <p>延迟队列实现原理：</p>
 * <pre>
 * 1. 创建延迟交换器（Topic Exchange）
 * 2. 按延迟时间创建延迟队列（TTL=延迟时间，无消费者）
 * 3. 延迟队列绑定死信交换器（DLX）
 * 4. 消息TTL过期后，通过DLX路由到目标队列
 * </pre>
 *
 * <p>队列命名规则：</p>
 * <ul>
 * <li>延迟交换器：{prefix}.exchange</li>
 * <li>延迟队列：{prefix}.{seconds}s.queue</li>
 * </ul>
 *
 * @author goya
 * @since 2025/6/25 16:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayQueueManager {

    private final RabbitAdmin rabbitAdmin;
    private final BusProperties busProperties;
    private final RabbitMQManagementTool managementTool;

    /**
     * 延迟交换器缓存
     */
    private volatile TopicExchange delayExchange;

    /**
     * 延迟队列缓存 - Key: 延迟秒数, Value: 队列实例
     */
    private final Map<Integer, Queue> delayQueues = new ConcurrentHashMap<>();

    /**
     * 延迟队列最后使用时间 - Key: 延迟秒数, Value: 最后使用时间戳
     */
    private final Map<Integer, Long> delayQueueLastUsed = new ConcurrentHashMap<>();

    /**
     * 获取或创建延迟交换器
     *
     * @return 延迟交换器
     */
    public TopicExchange getOrCreateDelayExchange() {
        if (delayExchange == null) {
            synchronized (this) {
                if (delayExchange == null) {
                    delayExchange = createDelayExchange();
                }
            }
        }
        return delayExchange;
    }

    /**
     * 获取或创建指定延迟时间的延迟队列
     *
     * @param delaySeconds     延迟秒数
     * @param targetExchange   目标交换器
     * @param targetRoutingKey 目标路由键
     * @return 延迟队列
     */
    public Queue getOrCreateDelayQueue(int delaySeconds, String targetExchange, String targetRoutingKey) {
        if (delaySeconds <= 0) {
            throw new IllegalArgumentException("延迟秒数必须大于0: " + delaySeconds);
        }

        // 检查是否超过最大延迟时间
        long delayMillis = delaySeconds * 1000L;
        if (delayMillis > busProperties.getRabbitmq().getMaxDelayMillis()) {
            throw new IllegalArgumentException(
                    String.format("延迟时间 %d 毫秒超过最大限制 %d 毫秒",
                            delayMillis, busProperties.getRabbitmq().getMaxDelayMillis()));
        }

        // 更新使用时间
        delayQueueLastUsed.put(delaySeconds, System.currentTimeMillis());

        // 先检查本地缓存
        Queue cachedQueue = delayQueues.get(delaySeconds);
        if (cachedQueue != null) {
            return cachedQueue;
        }

        // 使用 RabbitMQ 服务检查队列是否已存在
        String queueName = getDelayQueueName(delaySeconds, targetRoutingKey);
        try {
            if (managementTool.queueExists(queueName)) {
                // 队列已存在，直接创建队列对象并缓存
                Map<String, Object> args = new HashMap<>();
                args.put("x-message-ttl", delaySeconds * 1000L);
                args.put("x-dead-letter-exchange", targetExchange);
                if (org.apache.commons.lang3.StringUtils.isNotBlank(targetRoutingKey)) {
                    args.put("x-dead-letter-routing-key", targetRoutingKey);
                }
                
                Queue existingQueue = QueueBuilder.durable(queueName).withArguments(args).build();
                delayQueues.put(delaySeconds, existingQueue);
                log.debug("延迟队列 '{}' 已存在于 RabbitMQ 服务器，使用现有队列", queueName);
                return existingQueue;
            }
        } catch (Exception e) {
            log.debug("检查延迟队列 '{}' 是否存在时出错: {}", queueName, e.getMessage());
        }

        // 队列不存在，需要创建
        try {
            Queue newQueue = createDelayQueue(delaySeconds, targetExchange, targetRoutingKey);
            delayQueues.put(delaySeconds, newQueue);
            return newQueue;
        } catch (Exception e) {
            log.error("创建延迟队列 '{}' 失败: {}", queueName, e.getMessage());
            throw new RuntimeException("创建延迟队列失败: " + queueName, e);
        }
    }

    /**
     * 创建延迟交换器
     */
    private TopicExchange createDelayExchange() {
        String exchangeName = busProperties.getRabbitmq().getDelayExchangeName();

        // 构建交换器
        ExchangeBuilder exchangeBuilder = ExchangeBuilder
                .topicExchange(exchangeName)
                .durable(busProperties.getRabbitmq().isDurableExchange());

        // 条件性设置autoDelete
        if (busProperties.getRabbitmq().isAutoDeleteExchange()) {
            exchangeBuilder = exchangeBuilder.autoDelete();
        }

        TopicExchange exchange = exchangeBuilder.build();

        rabbitAdmin.declareExchange(exchange);

        log.info("创建延迟交换器: {}", exchangeName);
        return exchange;
    }

    /**
     * 创建延迟队列
     *
     * @param delaySeconds     延迟秒数
     * @param targetExchange   目标交换器
     * @param targetRoutingKey 目标路由键
     */
    private Queue createDelayQueue(int delaySeconds, String targetExchange, String targetRoutingKey) {
        String queueName = getDelayQueueName(delaySeconds, targetRoutingKey);
        long ttlMillis = delaySeconds * 1000L;

        // 设置队列参数
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ttlMillis); // 消息TTL
        args.put("x-dead-letter-exchange", targetExchange); // 死信交换器

        if (org.apache.commons.lang3.StringUtils.isNotBlank(targetRoutingKey)) {
            args.put("x-dead-letter-routing-key", targetRoutingKey); // 死信路由键
        }

        Queue queue = QueueBuilder
                .durable(queueName)
                .withArguments(args)
                .build();

        rabbitAdmin.declareQueue(queue);

        // 绑定到延迟交换器
        Binding binding = BindingBuilder
                .bind(queue)
                .to(getOrCreateDelayExchange())
                .with(getDelayRoutingKey(delaySeconds,targetRoutingKey));

        rabbitAdmin.declareBinding(binding);

        log.info("创建延迟队列: {}, TTL: {}毫秒, 目标交换器: {}, 目标路由键: {}",
                queueName, ttlMillis, targetExchange, targetRoutingKey);

        return queue;
    }

    /**
     * 获取延迟队列名称
     */
    private String getDelayQueueName(int delaySeconds, String topic) {
        return PropertyResolver.getApplicationName(SpringUtil.getApplicationContext().getEnvironment()) + busProperties.getRabbitmq().getDelayQueuePrefix() + "." + topic + "." + delaySeconds + "s.queue";
    }

    /**
     * 获取延迟队列路由键
     */
    private String getDelayRoutingKey(int delaySeconds, String topic) {
        return "delay." + topic + "." + delaySeconds + "s";
    }

    /**
     * 发送消息到延迟队列
     *
     * @param delaySeconds     延迟秒数
     * @param targetExchange   目标交换器
     * @param targetRoutingKey 目标路由键
     * @return 延迟队列路由键
     */
    public String getDelayQueueRoutingKey(int delaySeconds, String targetExchange, String targetRoutingKey) {
        // 确保延迟队列存在
        getOrCreateDelayQueue(delaySeconds, targetExchange, targetRoutingKey);
        return getDelayRoutingKey(delaySeconds,targetRoutingKey);
    }

    /**
     * 检查延迟队列功能是否启用
     */
    public boolean isDelayEnabled() {
        return busProperties.getRabbitmq().isEnabled() &&
                busProperties.getRabbitmq().isDelayEnabled();
    }

    /**
     * 定期清理空闲的延迟队列
     * 每小时执行一次
     */
    @Scheduled(fixedDelayString = "#{@busProperties.rabbitmq.delayQueueCleanupInterval * 1000}")
    public void cleanupIdleDelayQueues() {
        if (!isDelayEnabled() || busProperties.getRabbitmq().getDelayQueueCleanupInterval() <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long idleThreshold = busProperties.getRabbitmq().getDelayQueueIdleTimeout() * 1000L;

        delayQueueLastUsed.entrySet().removeIf(entry -> {
            int delaySeconds = entry.getKey();
            long lastUsed = entry.getValue();

            if (now - lastUsed > idleThreshold) {
                try {
                    String queueName = getDelayQueueName(delaySeconds, "");
                    rabbitAdmin.deleteQueue(queueName);
                    delayQueues.remove(delaySeconds);

                    log.info("清理空闲延迟队列: {}, 空闲时间: {}毫秒", queueName, now - lastUsed);
                    return true;
                } catch (Exception e) {
                    log.warn("清理延迟队列失败: {}", getDelayQueueName(delaySeconds, ""), e);
                    return false;
                }
            }
            return false;
        });
    }

    /**
     * 验证延迟时间是否有效
     *
     * @param delayMillis 延迟毫秒数
     * @throws IllegalArgumentException 如果延迟时间无效
     */
    public void validateDelayTime(long delayMillis) {
        if (delayMillis <= 0) {
            throw new IllegalArgumentException("延迟时间必须大于0: " + delayMillis);
        }

        if (delayMillis > busProperties.getRabbitmq().getMaxDelayMillis()) {
            throw new IllegalArgumentException(
                    String.format("延迟时间 %d 毫秒超过最大限制 %d 毫秒",
                            delayMillis, busProperties.getRabbitmq().getMaxDelayMillis()));
        }
    }

    /**
     * 获取延迟交换器名称
     */
    public String getDelayExchangeName() {
        return busProperties.getRabbitmq().getDelayExchangeName();
    }
} 