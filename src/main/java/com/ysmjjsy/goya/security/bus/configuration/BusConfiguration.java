package com.ysmjjsy.goya.security.bus.configuration;

import cn.hutool.extra.spring.SpringUtil;
import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.core.DefaultEventBus;
import com.ysmjjsy.goya.security.bus.core.EventListenerBeanPostProcessor;
import com.ysmjjsy.goya.security.bus.core.LocalEventBus;
import com.ysmjjsy.goya.security.bus.decision.DefaultMessageConfigDecisionEngine;
import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.duplicate.MessageDeduplicator;
import com.ysmjjsy.goya.security.bus.route.DefaultRoutingStrategy;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategy;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategyManager;
import com.ysmjjsy.goya.security.bus.serializer.JsonMessageSerializer;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.store.MessageStore;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQRoutingStrategy;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQTransport;
import com.ysmjjsy.goya.security.bus.transport.redis.RedisMessageDeduplicator;
import com.ysmjjsy.goya.security.bus.transport.redis.RedisMessageStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 15:05
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@Import(SpringUtil.class)
@EnableConfigurationProperties(BusProperties.class)
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BusConfiguration {

    @PostConstruct
    public void init() {
        log.info("config [bus] | load the bus configuration");
    }

    @Bean
    public EventListenerBeanPostProcessor eventListenerBeanPostProcessor(IEventBus iEventBus,
                                                                         BusProperties properties,
                                                                         LocalEventBus localEventBus,
                                                                         MessageConfigDecision messageConfigDecision
    ) {
        return new EventListenerBeanPostProcessor(iEventBus, properties, localEventBus, messageConfigDecision);
    }

    /**
     * 消息序列化器
     *
     * @return 消息序列化器
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageSerializer messageSerializer() {
        return new JsonMessageSerializer();
    }

    /**
     * 消息配置智能决策引擎
     *
     * @return 消息配置智能决策引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageConfigDecision messageConfigDecision(BusProperties busProperties) {
        return new DefaultMessageConfigDecisionEngine(busProperties);
    }

    @Bean
    public DefaultRoutingStrategy defaultRoutingStrategy(ApplicationContext applicationContext, BusProperties busProperties) {
        return new DefaultRoutingStrategy(applicationContext, busProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategyManager routingStrategyManager(Map<String, RoutingStrategy> routingStrategyMap) {
        return new RoutingStrategyManager(routingStrategyMap);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalEventBus localEventBus() {
        return new LocalEventBus();
    }

    @Bean
    @ConditionalOnMissingBean
    public IEventBus iEventBus(MessageConfigDecision messageConfigDecision,
                               TaskExecutor busTaskExecutor,
                               LocalEventBus localEventBus,
                               MessageSerializer messageSerializer,
                               MessageStore messageStore,
                               MessageDeduplicator messageDeduplicator) {
        return new DefaultEventBus(
                messageConfigDecision,
                busTaskExecutor,
                localEventBus,
                messageSerializer,
                messageStore,
                messageDeduplicator
        );
    }

    /**
     * 创建框架专用线程池
     *
     * @param properties 配置属性
     * @return 线程池执行器
     */
    @Bean("busTaskExecutor")
    public TaskExecutor busTaskExecutor(BusProperties properties) {
        BusProperties.Executor executorConfig = properties.getExecutor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(executorConfig.getCorePoolSize());
        executor.setMaxPoolSize(executorConfig.getMaxPoolSize());
        executor.setQueueCapacity(executorConfig.getQueueCapacity());
        executor.setThreadNamePrefix(executorConfig.getThreadNamePrefix());
        executor.setKeepAliveSeconds(executorConfig.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Unified MQ TaskExecutor initialized with corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executorConfig.getCorePoolSize(),
                executorConfig.getMaxPoolSize(),
                executorConfig.getQueueCapacity());

        return executor;
    }

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RabbitMqConfiguration {

        @PostConstruct
        public void init() {
            log.info("config [bus] | load [rabbitmq] configuration");
        }

        @Bean
        public RabbitMQTransport rabbitMQTransport(RabbitTemplate rabbitTemplate,
                                                   ConnectionFactory connectionFactory,
                                                   @Qualifier("rabbitMQRoutingStrategy") RoutingStrategy routingStrategy
        ) {
            return new RabbitMQTransport(rabbitTemplate, connectionFactory, routingStrategy);
        }

        @Bean
        public RabbitMQRoutingStrategy rabbitMQRoutingStrategy(ApplicationContext applicationContext, BusProperties busProperties) {
            return new RabbitMQRoutingStrategy(applicationContext, busProperties);
        }
    }

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "bus.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RedisConfiguration {

        @PostConstruct
        public void init() {
            log.info("config [bus] | load [redis] configuration");
        }

        @Bean
        @ConditionalOnMissingBean
        public MessageStore messageStore() {
            return new RedisMessageStore();
        }

        @Bean
        @ConditionalOnMissingBean
        public MessageDeduplicator messageDeduplicator() {
            return new RedisMessageDeduplicator();
        }
    }
}
