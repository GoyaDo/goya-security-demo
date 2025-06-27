package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.core.MessageConfigHint;
import com.ysmjjsy.goya.security.bus.enums.MessageModel;
import com.ysmjjsy.goya.security.bus.resolver.PropertyResolver;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 16:38
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractRoutingStrategy implements RoutingStrategy {

    private final ApplicationContext applicationContext;
    private final BusProperties busProperties;

    @Override
    public RoutingContext buildSendingContext(IEvent event, MessageConfigHint hint) {
        return determineRoutingContext(event.getEventType(),hint.getMessageModel());
    }

    @Override
    public RoutingContext buildSubscriptionContext(SubscriptionConfig config) {
        // 确定业务域
        return determineRoutingContext(config.getEventType(),config.getMessageModel());
    }

    /**
     * 确定事件的业务域
     */
    private RoutingContext determineRoutingContext(String eventType, MessageModel messageModel) {
        final String applicationName = PropertyResolver.getApplicationName(applicationContext.getEnvironment());
        final String busPrefix = busProperties.getBusPrefix();
        final String businessDomain = applicationName + "." + busPrefix + "-" + eventType;

        String consumerGroup = "queue-" + eventType;
        return RoutingContext.builder()
                .businessDomain(businessDomain)
                .eventType(eventType)
                .consumerGroup(consumerGroup)
                .routingSelector(eventType)
                .messageModel(messageModel)
                .build();
    }
}
