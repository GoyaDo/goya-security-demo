package com.ysmjjsy.goya.security.bus.domain;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import com.ysmjjsy.goya.security.bus.enums.EventType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>统一事件接口</p>
 * 扩展Spring ApplicationEvent，支持序列化和远程传输
 *
 * @author goya
 * @since 2025/6/24 15:40
 */
@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
public abstract class IEvent implements Serializable {

    private static final long serialVersionUID = 5801852503405243664L;

    /**
     * 事件ID
     */
    protected final String eventId;

    /**
     * 事件类型
     */
    protected final EventType eventType;

    /**
     * 事件时间
     */
    protected final LocalDateTime eventTime = LocalDateTime.now();

    /**
     * 事件主题
     */
    protected String topic;

    /**
     * 事件路由策略
     */
    protected EventRoutingStrategy routingStrategy;

    /**
     * 远程传输类型
     */
    protected BusRemoteType remoteType;

    protected IEvent(String eventId, EventType eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    protected IEvent() {
        this(IdUtil.getSnowflakeNextIdStr(), EventType.DEFAULT);
    }

    protected IEvent(EventType eventType) {
        this(IdUtil.getSnowflakeNextIdStr(),eventType);
    }

    public void topic(String topic) {
        this.topic = topic;
    }

    public void routingStrategy(EventRoutingStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public void remoteType(BusRemoteType remoteType) {
        this.remoteType = remoteType;
    }
}
