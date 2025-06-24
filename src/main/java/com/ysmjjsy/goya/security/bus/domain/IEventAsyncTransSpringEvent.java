package com.ysmjjsy.goya.security.bus.domain;

import com.ysmjjsy.goya.security.bus.route.EventRoutingDecision;
import lombok.Getter;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 20:53
 */
@Getter
public class IEventAsyncTransSpringEvent extends IEventSpringEvent {

    private static final long serialVersionUID = -4866131480276824996L;

    public IEventAsyncTransSpringEvent(Object source, IEvent iEvent, EventRoutingDecision routingDecision) {
        super(source, iEvent,routingDecision);
    }
}
