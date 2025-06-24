package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import com.ysmjjsy.goya.security.bus.properties.BusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 默认事件路由器实现
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultEventRouter implements EventRouter {

    private final BusProperties busProperties;

    @Override
    public EventRoutingDecision route(IEvent event) {

        EventRoutingStrategy strategy = event.getRoutingStrategy();
        if (Objects.isNull(strategy)){
            strategy = busProperties.getDefaultRoutingStrategy();
        }

        log.debug("Routing event {} with strategy {}", event, strategy);

        String topic = event.getTopic();
        if (StringUtils.isBlank(topic)) {
            topic = busProperties.getDefaultTopic();
        }

        BusRemoteType remoteType = event.getRemoteType();
        if (Objects.isNull(remoteType)) {
            remoteType = busProperties.getDefaultRemoteType();
        }

        EventRoutingDecision decision;
        switch (strategy) {
            case LOCAL_ONLY: {
                decision = EventRoutingDecision.localOnly(topic);
                break;
            }
            case REMOTE_ONLY: {
                decision = EventRoutingDecision.remoteOnly(topic, remoteType);
                break;
            }
            case LOCAL_AND_REMOTE: {
                decision = EventRoutingDecision.localAndRemote(topic, remoteType);
                break;
            }
            default: {
                log.error("Unsupported routing strategy {}", strategy);
                decision = EventRoutingDecision.localOnly(topic);
                break;
            }
        }
        return decision;
    }
}