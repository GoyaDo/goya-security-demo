package com.ysmjjsy.goya.security.bus.domain;

import com.ysmjjsy.goya.security.bus.route.EventRoutingDecision;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 20:52
 */
@Getter
public abstract class IEventSpringEvent extends ApplicationEvent {

    private static final long serialVersionUID = -7744109046874639567L;
    
    protected final IEvent iEvent;

    protected final EventRoutingDecision eventRoutingDecision;

    protected IEventSpringEvent(Object source, IEvent iEvent, EventRoutingDecision eventRoutingDecision) {
        super(source);
        this.iEvent = iEvent;
        this.eventRoutingDecision = eventRoutingDecision;
    }
}
