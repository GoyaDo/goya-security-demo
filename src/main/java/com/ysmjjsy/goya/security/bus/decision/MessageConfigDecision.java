package com.ysmjjsy.goya.security.bus.decision;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.core.MessageConfigHint;
import com.ysmjjsy.goya.security.bus.enums.TransportType;
import com.ysmjjsy.goya.security.bus.transport.MessageTransport;

import java.util.Map;

/**
 * <p>消息配置智能决策引擎</p>
 *
 * @author goya
 * @since 2025/6/27 15:11
 */
public interface MessageConfigDecision {

    /**
     * 决策消息配置
     *
     * @param event 事件
     * @param hint  配置提示
     * @return 决策结果
     */
    DecisionResult decide(IEvent event, MessageConfigHint hint);

    /**
     * 获取已注册的传输层
     *
     * @return 已注册的传输层
     */
    Map<TransportType, MessageTransport> getRegisteredTransports();
}
