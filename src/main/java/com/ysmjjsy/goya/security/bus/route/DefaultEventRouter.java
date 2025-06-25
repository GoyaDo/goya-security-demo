package com.ysmjjsy.goya.security.bus.route;

import cn.hutool.core.util.IdUtil;
import com.ysmjjsy.goya.security.bus.context.PropertyResolver;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import com.ysmjjsy.goya.security.bus.enums.EventType;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

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
    private final ApplicationContext applicationContext;

    @Override
    public EventRoutingDecision route(IEvent<?> event) {

        EventRoutingStrategy strategy = event.getRoutingStrategy();
        if (Objects.isNull(strategy)) {
            strategy = busProperties.getDefaultRoutingStrategy();
        }

        log.debug("Routing event {} with strategy {}", event, strategy);

        EventRoutingDecision decision = createEventRouter(strategy);

        if (StringUtils.isBlank(event.getEventId())) {
            decision.setEventId(IdUtil.getSnowflakeNextIdStr());
        } else {
            decision.setEventId(event.getEventId());
        }

        if (Objects.isNull(event.getEventType())) {
            decision.setEventType(EventType.DEFAULT);
        } else {
            decision.setEventType(event.getEventType());
        }

        String topic = event.getTopic();
        if (StringUtils.isBlank(topic)) {
            topic = busProperties.getDefaultTopic();
        }
        decision.setTopic(topic);

        BusRemoteType remoteType = event.getRemoteType();
        if (Objects.isNull(remoteType)) {
            remoteType = busProperties.getDefaultRemoteType();
        }

        decision.setRemoteType(remoteType);

        // 原始服务
        decision.setOriginalService(PropertyResolver.getApplicationName(applicationContext.getEnvironment()));

        // 目标服务
        if (decision.shouldPublishLocal()){
            decision.setDestinationService(decision.getOriginalService());
        }
        return decision;
    }
}