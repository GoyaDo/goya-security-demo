package com.ysmjjsy.goya.security.bus.configuration;

import cn.hutool.extra.spring.SpringUtil;
import com.ysmjjsy.goya.security.bus.bus.AbstractIEventBus;
import com.ysmjjsy.goya.security.bus.bus.DefaultIEventBus;
import com.ysmjjsy.goya.security.bus.bus.IEventBus;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.EventListenerAutoRegistrar;
import com.ysmjjsy.goya.security.bus.route.DefaultEventRouter;
import com.ysmjjsy.goya.security.bus.route.EventRouter;
import com.ysmjjsy.goya.security.bus.serializer.EventSerializer;
import com.ysmjjsy.goya.security.bus.serializer.JacksonEventSerializer;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQEventTransport;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQManagementTool;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMQRetryQueueListener;
import com.ysmjjsy.goya.security.bus.transport.rabbitmq.RabbitMqConfigResolver;
import com.ysmjjsy.goya.security.bus.transport.redis.RedisEventTransport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.List;

/**
 * <p>事件消息总线配置</p>
 *
 * @author goya
 * @since 2025/6/24 14:37
 */
@Slf4j
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BusProperties.class)
@ConditionalOnProperty(prefix = "bus", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(SpringUtil.class)
public class BusConfiguration {

    @PostConstruct
    public void init() {
        log.info("config [bus] | load the bus configuration");
    }

    @Bean
    @ConditionalOnMissingBean
    public EventSerializer eventSerializer() {
        return new JacksonEventSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventRouter eventRouter(BusProperties busProperties, ApplicationContext applicationContext) {
        return new DefaultEventRouter(busProperties, applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    public AbstractIEventBus.SpringApplicationEventListener springApplicationEventListener(IEventBus iEventBus, List<EventTransport> eventTransports) {
        return new AbstractIEventBus.SpringApplicationEventListener(iEventBus, eventTransports);
    }

    @Bean
    @ConditionalOnMissingBean
    public IEventBus iEventBus(ApplicationEventPublisher applicationEventPublisher,
                               List<EventTransport> eventTransports,
                               EventRouter eventRouter) {
        log.debug("config [bus] | load the bus: defaultIEventBus");
        return new DefaultIEventBus(applicationEventPublisher, eventTransports, eventRouter);
    }

    /**
     * 智能事件监听器自动注册器
     */
    @Bean
    public EventListenerAutoRegistrar eventListenerAutoRegistrar(BusProperties busProperties) {
        log.info("Enabling smart event listener auto-registration using SmartInitializingSingleton");
        return new EventListenerAutoRegistrar(busProperties);
    }

    @ConditionalOnProperty(prefix = "bus.redis", name = "enabled", havingValue = "true")
    @Configuration(proxyBeanMethods = false)
    static class RedisBusConfiguration{
        @Bean
        public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            log.trace("[Goya] |- Bean [Redis Message Listener Container] Configure.");
            return container;
        }

        @Bean
        public RedisEventTransport redisEventTransport(RedisTemplate<String, String> redisTemplate,
                                                       EventSerializer eventSerializer,
                                                       @Qualifier("redisMessageListenerContainer") RedisMessageListenerContainer messageListenerContainer) {
            log.info("Creating Redis event transport");
            RedisEventTransport transport = new RedisEventTransport(redisTemplate, eventSerializer, messageListenerContainer);
            transport.start();
            return transport;
        }
    }

    @ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true")
    @Configuration(proxyBeanMethods = false)
    static class RabbitMqBusConfiguration{

        /**
         * RabbitMQ 管理器
         * 用于声明和管理RabbitMQ资源（交换器、队列、绑定等）
         */
        @Bean
        @ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true")
        public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            RabbitAdmin admin = new RabbitAdmin(connectionFactory);

            // 设置自动启动
            admin.setAutoStartup(true);

            log.info("Created RabbitAdmin for event transport");
            return admin;
        }

        @Bean
        @ConditionalOnMissingBean
        public RabbitMqConfigResolver rabbitMqConfigResolver(BusProperties busProperties) {
            return new RabbitMqConfigResolver(busProperties);
        }

        /**
         * RabbitMQ 事件传输
         * 启动时自动启动传输服务
         */
        @Bean
        @ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true")
        public RabbitMQEventTransport rabbitMQEventTransport(RabbitTemplate rabbitTemplate,
                                                             RabbitAdmin rabbitAdmin,
                                                             ConnectionFactory connectionFactory,
                                                             EventSerializer eventSerializer,
                                                             BusProperties busProperties,
                                                             RabbitMqConfigResolver rabbitMqConfigResolver,
                                                             @Qualifier("rabbitMQManagementTool") RabbitMQManagementTool managementTool) {
            log.info("Creating RabbitMQ event transport with exchange: {}",
                    busProperties.getRabbitmq().getDefaultExchangeName());

            RabbitMQEventTransport transport = new RabbitMQEventTransport(
                    rabbitTemplate, rabbitAdmin, connectionFactory, eventSerializer, busProperties, rabbitMqConfigResolver, managementTool);

            // 启动传输服务
            transport.start();

            return transport;
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "bus.rabbitmq", name = "retry-queue", havingValue = "true")
        public RabbitMQRetryQueueListener rabbitMQRetryQueueListener(RabbitTemplate rabbitTemplate){
            return new RabbitMQRetryQueueListener(rabbitTemplate);
        }

        /**
         * RabbitMQ 管理工具
         */
        @Bean("rabbitMQManagementTool")
        @ConditionalOnProperty(prefix = "bus.rabbitmq", name = "enabled", havingValue = "true")
        public RabbitMQManagementTool rabbitMQManagementTool(RabbitAdmin rabbitAdmin, BusProperties busProperties) {
            log.info("Creating RabbitMQ management tool");
            return new RabbitMQManagementTool(rabbitAdmin, busProperties);
        }
    }
}
