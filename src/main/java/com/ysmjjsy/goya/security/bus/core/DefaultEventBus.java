package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.context.MessageTransportContext;
import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.store.EventStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.retry.support.RetryTemplate;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 17:49
 */
@Slf4j
public class DefaultEventBus extends AbstractEventBus {

    public DefaultEventBus(MessageConfigDecision messageConfigDecision,
                           TaskExecutor busTaskExecutor,
                           LocalEventBus localEventBus,
                           MessageSerializer messageSerializer,
                           EventStore messageStore,
                           RetryTemplate retryTemplate,
                           MessageTransportContext messageTransportContext,
                           ApplicationContext applicationContext
) {
        super(messageConfigDecision, busTaskExecutor,
                localEventBus, messageSerializer, messageStore, retryTemplate, messageTransportContext,applicationContext);
    }
}
