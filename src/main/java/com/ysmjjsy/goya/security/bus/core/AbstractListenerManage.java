package com.ysmjjsy.goya.security.bus.core;

import cn.hutool.core.map.MapUtil;
import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.MessageTransportContext;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
import com.ysmjjsy.goya.security.bus.enums.EventStatus;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.route.RoutingContext;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategy;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategyManager;
import com.ysmjjsy.goya.security.bus.serializer.JsonMessageSerializer;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/7/3 15:34
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractListenerManage implements ListenerManage {

    protected final BusProperties busProperties;
    protected final RoutingStrategyManager routingStrategyManager;
    protected final MessageTransportContext messageTransportContext;

    @Override
    public SubscriptionConfig getSubscriptionConfig(EventModel eventModel,
                                                    TransportType transportType,
                                                    Map<String, Object> properties,
                                                    String eventKey,
                                                    RoutingContext routingContext,
                                                    Class<? extends IEvent> event) {
        if (transportType == TransportType.LOCAL) {
            transportType = busProperties.getDefaultTransport();
        }

        SubscriptionConfig config = SubscriptionConfig.builder()
                .eventModel(eventModel)
                .eventKey(eventKey)
                .transportType(transportType)
                .eventClassSimpleName(event.getSimpleName())
                .eventClass(event)
                .build();

        if (Objects.isNull(routingContext)) {

            RoutingStrategy routingStrategy = routingStrategyManager.selectStrategy(transportType);

            // 使用路由策略管理器构建订阅路由上下文
            routingContext = routingStrategy.buildSubscriptionContext(config);
        }
        config.setRoutingContext(routingContext);
        if (MapUtil.isNotEmpty(properties)) {
            config.setProperties(properties);
        }

        return config;
    }

    protected SubscriptionConfig getSubscriptionConfig(EventModel eventModel,
                                                       TransportType transportType,
                                                       Map<String, Object> properties,
                                                       String eventKey,
                                                       Class<? extends IEvent> event) {
        return getSubscriptionConfig(eventModel, transportType, properties, eventKey, null, event);
    }

    @Override
    public SubscriptionConfig subscribeByConfig(EventModel eventModel,
                                                TransportType transportType,
                                                Map<String, Object> properties,
                                                String eventKey,
                                                IEventListener<? extends IEvent> listener,
                                                Class<? extends IEvent> event) {

        // 获取对应的传输层
        SubscriptionConfig subscriptionConfig = getSubscriptionConfig(eventModel, transportType, properties, eventKey, event);

        // 创建mq信息
        createMqInfosAfter(subscriptionConfig);
        subscribeToTransport(subscriptionConfig, listener, eventKey, event);
        log.debug("Built subscription config: {}", subscriptionConfig);
        return subscriptionConfig;
    }

    /**
     * 获取用于订阅的传输层
     */
    public MessageTransport getTransportForSubscription(TransportType transportType) {

        Map<TransportType, MessageTransport> transports = messageTransportContext.getTransportRegistry();

        log.debug("Available transports: {}", transports.keySet());

        // 优先使用annotation中指定的传输层
        if (transportType != null && transports.containsKey(transportType)) {
            return transports.get(transportType);
        }

        // 选择默认传输层
        TransportType defaultTransport = busProperties.getDefaultTransport();
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
    protected static class MessageConsumerWrapper implements MessageConsumer {

        @Getter
        private final IEventListener<IEvent> listener;
        private final String eventKey;
        private final MessageSerializer messageSerializer;
        private final Class<? extends IEvent> eventClass;

        @SuppressWarnings("unchecked")
        public MessageConsumerWrapper(IEventListener<? extends IEvent> listener, String eventKey, Class<? extends IEvent> eventClass) {
            this.listener = (IEventListener<IEvent>) listener;
            this.eventKey = eventKey;
            this.messageSerializer = new JsonMessageSerializer();
            this.eventClass = eventClass;
        }

        @Override
        public ConsumeResult consume(TransportEvent message) {
            try {
                // 尝试反序列化为具体的事件对象
                IEvent event = deserializeEvent(message);
                event.setEventStatus(EventStatus.SUCCESS);
                ConsumeResult result = listener.onEvent(event);
                log.debug("Message consumed with result: {} for event type: {}", result, eventKey);

                return result;
            } catch (Exception e) {
                log.error("Failed to consume message for event type: {}", eventKey, e);
                return ConsumeResult.RETRY;
            }
        }

        private IEvent deserializeEvent(TransportEvent message) {
            try {
                // 尝试从消息头获取事件类名
                String eventClassName = message.getEventClass();

                // 如果有具体的事件类名，尝试反序列化为具体类型
                if (eventClassName != null) {
                    try {
                        return messageSerializer.deserialize(message.getBody(), eventClass);
                    } catch (Exception e) {
                        log.warn("Event class not found: {}, falling back to generic wrapper", eventClassName);
                    }
                }

                // 回退到通用包装器
                return new SimpleEventWrapper(message, eventKey);

            } catch (Exception e) {
                log.warn("Failed to deserialize event, using generic wrapper", e);
                return new SimpleEventWrapper(message, eventKey);
            }
        }
    }

    /**
     * 简单的事件包装器（临时实现）
     */
    private static class SimpleEventWrapper implements IEvent {

        private final TransportEvent message;
        private final String eventKey;

        public SimpleEventWrapper(TransportEvent message, String eventKey) {
            this.message = message;
            this.eventKey = eventKey;
        }

        @Override
        public String getEventId() {
            return "";
        }

        @Override
        public String getEventKey() {
            return "";
        }

        @Override
        public LocalDateTime getCreateTime() {
            return null;
        }

        @Override
        public EventStatus getEventStatus() {
            return null;
        }

        @Override
        public void setEventStatus(EventStatus eventStatus) {

        }
    }
}
