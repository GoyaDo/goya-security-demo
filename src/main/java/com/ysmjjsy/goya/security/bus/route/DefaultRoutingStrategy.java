package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.configuration.properties.BusProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

/**
 * 基于业务域的路由策略
 * 
 * 按业务域组织Exchange和Queue，实现业务隔离
 * 
 * @author goya
 * @since 2025/6/26
 */
@Slf4j
public class DefaultRoutingStrategy extends AbstractRoutingStrategy {

    public DefaultRoutingStrategy(ApplicationContext applicationContext, BusProperties busProperties) {
        super(applicationContext, busProperties);
    }
}

