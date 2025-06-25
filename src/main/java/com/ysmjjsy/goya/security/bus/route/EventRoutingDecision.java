package com.ysmjjsy.goya.security.bus.route;

import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import com.ysmjjsy.goya.security.bus.enums.EventType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

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
     * 事件ID
     */
    @Getter
    @Setter
    protected String eventId;

    /**
     * 事件类型
     */
    @Getter
    @Setter
    protected EventType eventType;

    /**
     * 事件时间
     */
    @Getter
    protected final LocalDateTime eventTime = LocalDateTime.now();

    /**
     * 事件主题
     */
    @Getter
    @Setter
    protected String topic;

    /**
     * 事件路由策略
     */
    @Getter
    @Setter
    protected EventRoutingStrategy routingStrategy;

    /**
     * 远程传输类型
     */
    @Getter
    @Setter
    protected BusRemoteType remoteType;

    /**
     * 原始服务
     */
    @Getter
    @Setter
    protected String originalService;

    /**
     * 目标服务
     */
    @Getter
    @Setter
    protected String destinationService;

    public boolean shouldPublishLocal() {
        return publishLocal;
    }

    public boolean shouldPublishRemote() {
        return publishRemote;
    }
}