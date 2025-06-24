package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 事件路由决策
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
@Slf4j
@RequiredArgsConstructor
public class EventRoutingDecision {

    /**
     * 是否发布本地事件
     */
    private final boolean publishLocal;

    /**
     * 是否发布远程事件
     */
    private final boolean publishRemote;

    /**
     * 发布的主题
     */
    @Getter
    private final String topic;

    /**
     * 发布策略
     */
    @Getter
    private final EventRoutingStrategy strategy;

    /**
     * 远程类型
     */
    @Getter
    private final BusRemoteType remoteType;


    public static EventRoutingDecision localOnly(String topic) {
        return new EventRoutingDecision(
                true,
                false,
                topic,
                EventRoutingStrategy.LOCAL_ONLY,
                null
        );
    }

    public static EventRoutingDecision remoteOnly(String topic, BusRemoteType remoteType) {
        return new EventRoutingDecision(
                false,
                true,
                topic,
                EventRoutingStrategy.REMOTE_ONLY,
                remoteType);
    }

    public static EventRoutingDecision localAndRemote(String topic, BusRemoteType remoteType) {
        return new EventRoutingDecision(
                true,
                true,
                topic,
                EventRoutingStrategy.LOCAL_AND_REMOTE,
                remoteType
        );
    }

    public boolean shouldPublishLocal() {
        return publishLocal;
    }

    public boolean shouldPublishRemote() {
        return publishRemote;
    }
}