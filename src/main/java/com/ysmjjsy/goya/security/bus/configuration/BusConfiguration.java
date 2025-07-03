package com.ysmjjsy.goya.security.bus.configuration;

import cn.hutool.extra.spring.SpringUtil;
import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.EventListenerBeanPostProcessor;
import com.ysmjjsy.goya.security.bus.context.MessageTransportContext;
import com.ysmjjsy.goya.security.bus.core.DefaultEventBus;
import com.ysmjjsy.goya.security.bus.core.DefaultListenerManage;
import com.ysmjjsy.goya.security.bus.core.LocalEventBus;
import com.ysmjjsy.goya.security.bus.decision.DefaultMessageConfigDecisionEngine;
import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.route.DefaultRoutingStrategy;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategy;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategyManager;
import com.ysmjjsy.goya.security.bus.serializer.JsonMessageSerializer;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.store.EventStore;
import com.ysmjjsy.goya.security.bus.transport.kafka.KafkaRoutingStrategy;
import com.ysmjjsy.goya.security.bus.transport.kafka.KafkaTransport;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQInfoManager;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQRoutingStrategy;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQTransport;
import com.ysmjjsy.goya.security.bus.transport.redis.RedisMessageDeduplicator;
import com.ysmjjsy.goya.security.bus.transport.redis.RedisMessageStore;
import com.ysmjjsy.goya.security.bus.transport.redis.RedisTransport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.Properties;
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
    public MessageTransportContext messageTransportContext() {
        return new MessageTransportContext();
    }

    @Bean
    public DefaultListenerManage defaultListenerManage(BusProperties busProperties, RoutingStrategyManager routingStrategyManager, MessageTransportContext messageTransportContext) {
        return new DefaultListenerManage(busProperties, routingStrategyManager, messageTransportContext);
    }

    @Bean
    public EventListenerBeanPostProcessor eventListenerBeanPostProcessor(
            BusProperties properties,
            LocalEventBus localEventBus,
            ApplicationContext applicationContext
    ) {
        return new EventListenerBeanPostProcessor(properties, localEventBus, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate(BusProperties busProperties) {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 1. 重试策略（最多重试3次）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        // 可被动态替换，如方法中再次设置
        retryPolicy.setMaxAttempts(busProperties.getDefaultRetryTimes());
        retryTemplate.setRetryPolicy(retryPolicy);

        // 2. 回退策略（每次重试间隔1000ms）
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        // 1 秒
        backOffPolicy.setBackOffPeriod(busProperties.getDefaultRetryDelay());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
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
    public MessageConfigDecision messageConfigDecision(BusProperties busProperties,
                                                       RoutingStrategyManager routingStrategyManager,
                                                       MessageTransportContext messageTransportContext) {
        return new DefaultMessageConfigDecisionEngine(busProperties, routingStrategyManager, messageTransportContext);
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
                               EventStore messageStore,
                               RetryTemplate retryTemplate,
                               MessageTransportContext messageTransportContext,
                               ApplicationContext applicationContext) {
        return new DefaultEventBus(
                messageConfigDecision,
                busTaskExecutor,
                localEventBus,
                messageSerializer,
                messageStore,
                retryTemplate,
                messageTransportContext,
                applicationContext
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
        @ConditionalOnMissingBean
        public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            return new RabbitAdmin(connectionFactory);
        }

        @Bean
        public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
            RabbitTemplate template = new RabbitTemplate(factory);
            template.setMandatory(true);
            template.setReturnsCallback((message) -> {
                log.error("RabbitMQ message returns: {}", message);
            });

            template.setConfirmCallback((correlationData, ack, cause) -> {
                if (!ack) {
                    System.err.println("ConfirmCallback - 消息未被Broker确认：" + cause);
                } else {
                    System.out.println("ConfirmCallback - 消息成功到达Broker");
                }
            });
            return template;
        }

        @Bean
        public RabbitMQTransport rabbitMQTransport(RabbitTemplate rabbitTemplate,
                                                   RabbitAdmin rabbitAdmin,
                                                   ConnectionFactory connectionFactory,
                                                   MessageSerializer messageSerializer
        ) {
            return new RabbitMQTransport(rabbitTemplate, rabbitAdmin, connectionFactory, messageSerializer);
        }


        @Bean
        public RabbitMQInfoManager rabbitMQInfoManager(BusProperties properties,
                                                       RoutingStrategyManager routingStrategyManager,
                                                       RabbitAdmin rabbitAdmin,
                                                       MessageTransportContext messageTransportContext,
                                                       MessageSerializer messageSerializer,
                                                       RabbitTemplate rabbitTemplate,
                                                       ConnectionFactory connectionFactory) {
            return new RabbitMQInfoManager(properties,
                    routingStrategyManager,
                    rabbitAdmin,
                    messageTransportContext,
                    messageSerializer,
                    rabbitTemplate,
                    connectionFactory);
        }

        @Bean
        public RabbitMQRoutingStrategy rabbitMQRoutingStrategy(ApplicationContext applicationContext, BusProperties busProperties) {
            return new RabbitMQRoutingStrategy(applicationContext, busProperties);
        }
    }

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "bus.redis", name = "enabled", havingValue = "true")
    static class RedisConfiguration {

        @PostConstruct
        public void init() {
            log.info("config [bus] | load [redis] configuration");
        }

        @Bean
        @ConditionalOnMissingBean
        public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            log.debug("Redis Message Listener Container configured");
            return container;
        }

        @Bean
        @ConditionalOnMissingBean
        public RedisMessageStore redisMessageStore(RedisTemplate<String, String> redisTemplate,
                                                   MessageSerializer messageSerializer,
                                                   BusProperties busProperties) {
            return new RedisMessageStore(redisTemplate, messageSerializer, busProperties);
        }

        @Bean
        @ConditionalOnMissingBean
        public RedisMessageDeduplicator redisMessageDeduplicator(RedisTemplate<String, String> redisTemplate) {
            return new RedisMessageDeduplicator(redisTemplate);
        }

        @Bean
        public RedisTransport redisTransport(RedisTemplate<String, String> redisTemplate,
                                             RedisConnectionFactory connectionFactory,
                                             MessageSerializer messageSerializer) {
            return new RedisTransport(redisTemplate, connectionFactory, messageSerializer);
        }
    }

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "bus.kafka", name = "enabled", havingValue = "true")
    static class KafkaConfiguration {

        @PostConstruct
        public void init() {
            log.info("config [bus] | load [kafka] configuration");
        }

        @Bean
        @ConditionalOnMissingBean
        public KafkaProducer<String, byte[]> kafkaProducer(BusProperties busProperties) {
            Properties props = new Properties();
            // 基础配置 - 应该从配置文件读取
            props.put("bootstrap.servers", "localhost:9092");
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

            // 性能配置
            props.put("acks", "1");
            props.put("retries", 3);
            props.put("batch.size", 16384);
            props.put("linger.ms", 5);
            props.put("buffer.memory", 33554432);
            props.put("enable.idempotence", true);
            props.put("max.in.flight.requests.per.connection", 5);

            log.debug("Kafka Producer configured with bootstrap.servers: {}", props.get("bootstrap.servers"));
            return new KafkaProducer<>(props);
        }

        @Bean
        public KafkaTransport kafkaTransport(KafkaProducer<String, byte[]> kafkaProducer,
                                             MessageSerializer messageSerializer) {
            return new KafkaTransport(kafkaProducer, messageSerializer);
        }

        @Bean
        public KafkaRoutingStrategy kafkaRoutingStrategy(ApplicationContext applicationContext,
                                                         BusProperties busProperties) {
            return new KafkaRoutingStrategy(applicationContext, busProperties);
        }
    }
}

