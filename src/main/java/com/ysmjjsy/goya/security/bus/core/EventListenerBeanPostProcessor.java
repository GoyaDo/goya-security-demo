package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.annotation.IListener;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventBus;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import com.ysmjjsy.goya.security.bus.enums.RoutingStrategy;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.serializer.JsonMessageSerializer;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportMessage;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 事件监听器Bean后处理器
 * <p>
 * 扫描Spring容器中标注了@IListener注解的Bean和方法
 * 自动注册到本地事件总线和远程传输层
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@RequiredArgsConstructor
public class EventListenerBeanPostProcessor implements BeanPostProcessor {

    private final IEventBus eventBus;
    private final BusProperties properties;
    private final LocalEventBus localEventBus;
    private final MessageConfigDecision messageConfigDecision;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        // 处理类级别的@IListener注解
        IListener classAnnotation = AnnotationUtils.findAnnotation(beanClass, IListener.class);
        if (classAnnotation != null && bean instanceof IEventListener) {
            processClassLevelListener(bean, classAnnotation, beanName);
        }

        // 处理方法级别的@IListener注解
        Method[] methods = beanClass.getDeclaredMethods();
        for (Method method : methods) {
            IListener methodAnnotation = AnnotationUtils.findAnnotation(method, IListener.class);
            if (methodAnnotation != null) {
                processMethodLevelListener(bean, method, methodAnnotation, beanName);
            }
        }

        return bean;
    }

    /**
     * 处理类级别的监听器
     */
    @SuppressWarnings("unchecked")
    private void processClassLevelListener(Object bean, IListener annotation, String beanName) {
        if (!annotation.enabled()) {
            log.debug("Listener {} is disabled, skipping registration", beanName);
            return;
        }

        try {
            IEventListener<? extends IEvent> listener = (IEventListener<? extends IEvent>) bean;

            // 解析事件类型
            String eventType = resolveEventTypeFromClass(bean.getClass(), annotation);
            if (!StringUtils.hasText(eventType)) {
                log.warn("Could not resolve event type for listener: {}", beanName);
                return;
            }

            // 注册到本地事件总线
            localEventBus.registerListener(eventType, listener);

            // 注册到远程传输层（如果需要）
            registerToRemoteTransport(annotation, eventType, listener);

            log.info("Registered class-level listener: {} for event type: {}", beanName, eventType);

        } catch (Exception e) {
            log.error("Failed to register class-level listener: {}", beanName, e);
        }
    }

    /**
     * 处理方法级别的监听器
     */
    private void processMethodLevelListener(Object bean, Method method, IListener annotation, String beanName) {
        if (!annotation.enabled()) {
            log.debug("Method listener {}.{} is disabled, skipping registration", beanName, method.getName());
            return;
        }

        try {
            // 验证方法签名
            if (!isValidListenerMethod(method)) {
                log.warn("Invalid listener method signature: {}.{}", beanName, method.getName());
                return;
            }

            // 解析事件类型
            String eventType = resolveEventTypeFromMethod(method, annotation);
            if (!StringUtils.hasText(eventType)) {
                log.warn("Could not resolve event type for method listener: {}.{}", beanName, method.getName());
                return;
            }

            // 创建方法监听器包装器
            MethodListenerWrapper wrapper = new MethodListenerWrapper(bean, method);

            // 注册到本地事件总线
            localEventBus.registerListener(eventType, wrapper);

            // 注册到远程传输层（如果需要）
            registerToRemoteTransport(annotation, eventType, wrapper);

            log.info("Registered method-level listener: {}.{} for event type: {}", beanName, method.getName(), eventType);

        } catch (Exception e) {
            log.error("Failed to register method-level listener: {}.{}", beanName, method.getName(), e);
        }
    }

    /**
     * 从类的泛型参数解析事件类型
     */
    private String resolveEventTypeFromClass(Class<?> listenerClass, IListener annotation) {
        return annotation.eventType();
    }

    /**
     * 从方法参数解析事件类型
     */
    private String resolveEventTypeFromMethod(Method method, IListener annotation) {
        // 优先使用注解中指定的事件类型
        if (StringUtils.hasText(annotation.eventType())) {
            return annotation.eventType();
        }

        // 从方法参数推断
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0 && IEvent.class.isAssignableFrom(paramTypes[0])) {
            return extractEventTypeFromClass(paramTypes[0]);
        }

        return null;
    }

    /**
     * 从事件类提取事件类型
     */
    private String extractEventTypeFromClass(Class<?> eventClass) {
        // 如果是具体的事件类，使用类名
        if (!eventClass.equals(IEvent.class)) {
            String className = eventClass.getSimpleName();
            // 移除Event后缀
            if (className.endsWith("Event")) {
                className = className.substring(0, className.length() - 5);
            }
            // 转换为点分隔的小写格式
            return camelToSnakeCase(className);
        }
        return null;
    }

    /**
     * 驼峰转蛇形命名
     */
    private String camelToSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1.$2").toLowerCase();
    }

    /**
     * 验证监听器方法签名
     */
    private boolean isValidListenerMethod(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Class<?> returnType = method.getReturnType();

        // 检查参数：应该有一个参数，且继承自IEvent
        if (paramTypes.length != 1) {
            return false;
        }
        if (!IEvent.class.isAssignableFrom(paramTypes[0])) {
            return false;
        }

        // 检查返回值：应该是ConsumeResult或void
        return returnType.equals(Void.TYPE) ||
                returnType.equals(ConsumeResult.class);
    }

    /**
     * 注册到远程传输层
     */
    private void registerToRemoteTransport(IListener annotation, String eventType,
                                           IEventListener<? extends IEvent> listener) {
        try {
            // 检查是否需要远程订阅
            if (!needsRemoteSubscription(annotation)) {
                log.debug("No remote subscription needed for event type: {}", eventType);
                return;
            }

            // 构建订阅配置
            SubscriptionConfig config = buildSubscriptionConfig(annotation, eventType);

            // 创建消息消费者包装器
            MessageConsumer consumer = new MessageConsumerWrapper(listener, eventType);

            // 获取对应的传输层
            MessageTransport transport = getTransportForSubscription(annotation);
            if (transport != null && transport.isHealthy()) {
                // 订阅到远程传输层
                transport.subscribe(config, consumer);
                log.info("Successfully registered remote subscription for event type: {} via {} with config: {}",
                        eventType, transport.getTransportType(), config);
            } else {
                if (transport == null) {
                    log.warn("No suitable transport found for remote subscription: {}, available transports: {}",
                            eventType, messageConfigDecision.getRegisteredTransports().keySet());
                } else {
                    log.warn("Transport not healthy for remote subscription: {}, transport: {}",
                            eventType, transport.getTransportType());
                }
            }

        } catch (Exception e) {
            log.error("Failed to register remote transport subscription for event type: {}", eventType, e);
        }
    }

    /**
     * 检查是否需要远程订阅
     */
    private boolean needsRemoteSubscription(IListener annotation) {

        // 如果指定了主题且路由策略不是LOCAL_ONLY，需要远程订阅
        if (StringUtils.hasText(annotation.eventType())) {
            boolean needRemote = properties.getRoutingStrategy() != RoutingStrategy.LOCAL_ONLY;
            log.debug("Topic specified: {}, routing strategy: {}, need remote: {}",
                    annotation.eventType(), properties.getRoutingStrategy(), needRemote);
            return needRemote;
        }

        // 根据全局路由策略决定
        boolean needRemote = properties.getRoutingStrategy() == RoutingStrategy.REMOTE_ONLY ||
                properties.getRoutingStrategy() == RoutingStrategy.HYBRID;
        log.debug("Using global routing strategy: {}, need remote: {}", properties.getRoutingStrategy(), needRemote);
        return needRemote;
    }

    /**
     * 构建订阅配置
     */
    private SubscriptionConfig buildSubscriptionConfig(IListener annotation, String eventType) {
        SubscriptionConfig config = SubscriptionConfig.builder()
                .messageModel(annotation.messageModel())
                .eventType(annotation.eventType())
                .transportType(annotation.transportType())
                .concurrency(annotation.concurrency())
                .batchSize(annotation.batchSize())
                .selector(StringUtils.hasText(annotation.selector()) ? annotation.selector() : null)
                .build();

        log.debug("Built subscription config: {}", config);
        return config;
    }

    /**
     * 获取用于订阅的传输层
     */
    private MessageTransport getTransportForSubscription(IListener annotation) {
        Map<TransportType, MessageTransport> transports = messageConfigDecision.getRegisteredTransports();

        log.debug("Available transports: {}", transports.keySet());

        // 优先使用默认传输层
        TransportType defaultTransport = properties.getDefaultTransport();
        MessageTransport transport = transports.get(defaultTransport);

        if (transport != null) {
            log.debug("Using default transport: {}", defaultTransport);
            return transport;
        }

        // 如果默认传输层不可用，选择第一个可用的传输层
        for (MessageTransport availableTransport : transports.values()) {
            if (availableTransport.isHealthy()) {
                log.debug("Using fallback transport: {}", availableTransport.getTransportType());
                return availableTransport;
            }
        }

        log.warn("No healthy transport available for subscription");
        return null;
    }

    /**
     * 消息消费者包装器
     */
    private static class MessageConsumerWrapper implements MessageConsumer {
        private final IEventListener<IEvent> listener;
        private final String eventType;
        private final MessageSerializer messageSerializer;

        @SuppressWarnings("unchecked")
        public MessageConsumerWrapper(IEventListener<? extends IEvent> listener, String eventType) {
            this.listener = (IEventListener<IEvent>) listener;
            this.eventType = eventType;
            this.messageSerializer = new JsonMessageSerializer();
        }

        @Override
        public ConsumeResult consume(TransportMessage message) {
            try {
                // 尝试反序列化为具体的事件对象
                IEvent event = deserializeEvent(message);

                ConsumeResult result = listener.onEvent(event);
                log.debug("Message consumed with result: {} for event type: {}", result, eventType);

                return result;
            } catch (Exception e) {
                log.error("Failed to consume message for event type: {}", eventType, e);
                return ConsumeResult.RETRY;
            }
        }

        private IEvent deserializeEvent(TransportMessage message) {
            try {
                // 尝试从消息头获取事件类名
                String eventClassName = null;
                if (message.getHeaders() != null) {
                    Object classNameObj = message.getHeaders().get("eventClass");
                    if (classNameObj != null) {
                        eventClassName = classNameObj.toString();
                    }
                }

                // 如果有具体的事件类名，尝试反序列化为具体类型
                if (eventClassName != null) {
                    try {
                        Class<?> eventClass = Class.forName(eventClassName);
                        if (IEvent.class.isAssignableFrom(eventClass)) {
                            @SuppressWarnings("unchecked")
                            Class<? extends IEvent> typedEventClass = (Class<? extends IEvent>) eventClass;
                            return messageSerializer.deserialize(message.getBody(), typedEventClass);
                        }
                    } catch (ClassNotFoundException e) {
                        log.warn("Event class not found: {}, falling back to generic wrapper", eventClassName);
                    }
                }

                // 回退到通用包装器
                return new SimpleEventWrapper(message, eventType);

            } catch (Exception e) {
                log.warn("Failed to deserialize event, using generic wrapper", e);
                return new SimpleEventWrapper(message, eventType);
            }
        }
    }

    /**
     * 简单的事件包装器（临时实现）
     */
    private static class SimpleEventWrapper implements IEvent {
        private final TransportMessage message;
        private final String eventType;

        public SimpleEventWrapper(TransportMessage message, String eventType) {
            this.message = message;
            this.eventType = eventType;
        }

        @Override
        public String getEventId() {
            return message.getMessageId();
        }

        @Override
        public String getEventType() {
            return eventType;
        }

        @Override
        public java.time.LocalDateTime getCreateTime() {
            return java.time.LocalDateTime.now();
        }

        @Override
        public Map<String, Object> getMetadata() {
            return message.getHeaders() != null ? message.getHeaders() : new java.util.HashMap<>();
        }

        @Override
        public Map<String, String> getProperties() {
            return message.getProperties() != null ? message.getProperties() : new java.util.HashMap<>();
        }

        @Override
        public void setEventId(String eventId) {

        }

        @Override
        public void setEventType(String eventType) {

        }

        @Override
        public void setProperty(String key, String value) {

        }

        @Override
        public void setMetadata(String key, Object value) {

        }
    }

    /**
     * 方法监听器包装器
     */
    private static class MethodListenerWrapper implements IEventListener<IEvent> {
        private final Object bean;
        private final Method method;

        public MethodListenerWrapper(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
            this.method.setAccessible(true);
        }

        @Override
        public ConsumeResult onEvent(IEvent event) {
            try {
                Object result = method.invoke(bean, event);
                if (result instanceof ConsumeResult) {
                    return (ConsumeResult) result;
                } else {
                    return ConsumeResult.SUCCESS;
                }
            } catch (Exception e) {
                log.error("Method listener execution failed", e);
                return ConsumeResult.RETRY;
            }
        }
    }
} 