package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.context.MessageTransportContext;
import com.ysmjjsy.goya.security.bus.route.RoutingStrategyManager;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;
import com.ysmjjsy.goya.security.bus.spi.TransportEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/7/3 15:46
 */
@Slf4j
public class DefaultListenerManage extends AbstractListenerManage {

    /**
     * Exchange缓存
     */
    private final Map<String, Object> listenerCache = new ConcurrentHashMap<>();


    public DefaultListenerManage(BusProperties busProperties, RoutingStrategyManager routingStrategyManager, MessageTransportContext messageTransportContext) {
        super(busProperties, routingStrategyManager, messageTransportContext);
    }

    @Override
    public void putCache(String name, Object object) {
        listenerCache.put(name, object);
    }

    @Override
    public <T> T getCache(String name, Class<T> clazz) {
        return (T) listenerCache.get(name);
    }

    @Override
    public void buildMqInfosAfter(TransportEvent transportEvent) {
    }

    @Override
    public void createMqInfosAfter(SubscriptionConfig config) {

    }

    @Override
    public void subscribeToTransport(SubscriptionConfig config, IEventListener<? extends IEvent> listener, String eventKey, Class<? extends IEvent> event) {

    }
}
