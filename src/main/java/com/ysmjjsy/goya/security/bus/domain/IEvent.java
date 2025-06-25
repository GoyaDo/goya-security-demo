package com.ysmjjsy.goya.security.bus.domain;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import com.ysmjjsy.goya.security.bus.enums.EventRoutingStrategy;
import com.ysmjjsy.goya.security.bus.enums.EventType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;



/**
 * <p>统一事件接口</p>
 * 扩展Spring ApplicationEvent，支持序列化和远程传输
 *
 * @author goya
 * @since 2025/6/24 15:40
 */
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
public abstract class IEvent<E extends IEvent<E>> implements Serializable {

    private static final long serialVersionUID = 5801852503405243664L;

    /**
     * 事件ID
     */
    protected String eventId;

    /**
     * 事件类型
     */
    protected EventType eventType;

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

    /**
     * 原始服务
     */
    protected String originalService;

    /**
     * 目标服务
     */
    protected String destinationService;

    @SuppressWarnings("unchecked")
    protected E self() {
        return (E) this;
    }

    public E eventId(String eventId) {
        this.eventId = eventId;
        return self();
    }

    public E eventType(EventType eventType) {
        this.eventType = eventType;
        return self();
    }


    public E topic(String topic) {
        this.topic = topic;
        return self();
    }

    public E routingStrategy(EventRoutingStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
        return self();
    }

    public E remoteType(BusRemoteType remoteType) {
        this.remoteType = remoteType;
        return self();
    }

    public E originalService(String originalService) {
        this.originalService = originalService;
        return self();
    }

    public E destinationService(String destinationService) {
        this.destinationService = destinationService;
        return self();
    }
}

