package com.ysmjjsy.goya.security.bus.transport.redis;

import com.ysmjjsy.goya.security.bus.enums.EventCapability;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.spi.MessageConsumer;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import com.ysmjjsy.goya.security.bus.spi.TransportResult;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/7/1 15:07
 */
@Slf4j
public class RedisTransport implements MessageTransport {

    @Override
    public TransportResult send(TransportEvent transportEvent) {
        return null;
    }

    @Override
    public void subscribe(SubscriptionConfig config, MessageConsumer consumer) {

    }

    @Override
    public TransportType getTransportType() {
        return null;
    }

    @Override
    public Set<EventCapability> getSupportedCapabilities() {
        return Set.of();
    }

    @Override
    public boolean isHealthy() {
        return false;
    }

    @Override
    public Map<String, Object> buildSubscriptionProperties(Annotation config) {
        return Map.of();
    }
}
