package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.core.MessageConfigHint;
import com.ysmjjsy.goya.security.bus.spi.SubscriptionConfig;

/**
 * 路由策略接口
 * 
 * 定义如何基于事件和配置提示构建路由上下文
 * 
 * @author goya
 * @since 2025/6/26
 */
public interface RoutingStrategy {
    
    /**
     * 构建发送消息的路由上下文
     * 
     * @param event 事件对象
     * @param hint 配置提示
     * @return 路由上下文
     */
    RoutingContext buildSendingContext(IEvent event, MessageConfigHint hint);

    /**
     * 构建订阅消息的路由上下文
     *
     * @param config 订阅配置
     * @return 路由上下文
     */
    RoutingContext buildSubscriptionContext(SubscriptionConfig config);
}
 