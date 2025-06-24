package com.ysmjjsy.goya.security.bus.bus;

import com.ysmjjsy.goya.security.bus.route.EventRouter;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 16:52
 */
@Slf4j
public class DefaultIEventBus extends AbstractIEventBus {


    public DefaultIEventBus(ApplicationEventPublisher applicationEventPublisher,
                            List<EventTransport> eventTransports,
                            EventRouter eventRouter) {
        super(applicationEventPublisher, eventTransports, eventRouter);
    }


}
