package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.decision.MessageConfigDecision;
import com.ysmjjsy.goya.security.bus.duplicate.MessageDeduplicator;
import com.ysmjjsy.goya.security.bus.serializer.MessageSerializer;
import com.ysmjjsy.goya.security.bus.store.MessageStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 17:49
 */
@Slf4j
public class DefaultEventBus extends AbstractEventBus{

    public DefaultEventBus(MessageConfigDecision messageConfigDecision,
                           TaskExecutor busTaskExecutor,
                           LocalEventBus localEventBus,
                           MessageSerializer messageSerializer,
                           MessageStore messageStore,
                           MessageDeduplicator messageDeduplicator) {
        super(messageConfigDecision, busTaskExecutor, localEventBus, messageSerializer, messageStore, messageDeduplicator);
    }
}
