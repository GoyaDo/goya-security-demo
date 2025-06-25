package com.ysmjjsy.goya.security.bus.route;


import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;

/**
 * 事件路由器接口
 * 决定事件的路由策略和目标
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
public interface EventRouter {

    /**
     * 创建事件路由器
     *
     * @param strategy 路由策略
     * @return 事件路由器
     */
    default EventRoutingDecision createEventRouter(EventRoutingStrategy strategy) {
        EventRoutingDecision decision;
        switch (strategy) {
            case REMOTE_ONLY: {
                decision = new EventRoutingDecision(false, true);
                break;
            }
            case LOCAL_AND_REMOTE: {
                decision = new EventRoutingDecision(true, true);
                break;
            }
            case LOCAL_ONLY:
            default: {
                decision = new EventRoutingDecision(true, false);
                break;
            }
        }
        decision.setRoutingStrategy(strategy);
        return decision;
    }

    /**
     * 路由事件，决定处理策略
     *
     * @param event 要路由的事件
     * @return 路由决策
     */
    EventRoutingDecision route(IEvent<?> event);

}