package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.enums.EventModel;
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
    public RoutingContext buildSendingContext(IEvent event, EventModel eventModel) {
        return determineRoutingContext(event.getEventKey(), eventModel);
    }

    @Override
    public RoutingContext buildSubscriptionContext(SubscriptionConfig config) {
        // 确定业务域
        return determineRoutingContext(config.getEventKey(), config.getEventModel());
    }

    /**
     * 确定事件的业务域
     */
    private RoutingContext determineRoutingContext(String eventKey, EventModel eventModel) {
        final String applicationName = PropertyResolver.getApplicationName(applicationContext.getEnvironment());
        final String busPrefix = busProperties.getBusPrefix();
        final String prefix = applicationName + "." + busPrefix;
        final String businessDomain = prefix + "-" + eventKey;

        String consumerGroup = prefix + ".queue-" + eventKey;
        return RoutingContext.builder()
                .businessDomain(businessDomain)
                .eventKey(eventKey)
                .consumerGroup(consumerGroup)
                .routingSelector(eventKey)
                .eventModel(eventModel)
                .build();
    }
}
