package com.ysmjjsy.goya.security.bus.transport.kafka;

import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import com.ysmjjsy.goya.security.bus.route.AbstractRoutingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/27 16:24
 */
@Slf4j
public class KafkaRoutingStrategy extends AbstractRoutingStrategy {

    public KafkaRoutingStrategy(ApplicationContext applicationContext, BusProperties busProperties) {
        super(applicationContext, busProperties);
    }
}
