package com.ysmjjsy.goya.security.bus.exception;

import lombok.Getter;
import lombok.ToString;

/**
 * <p>事件异常</p>
 *
 * @author goya
 * @since 2025/6/24 17:13
 */
@ToString
@Getter
public class EventHandleException extends RuntimeException {

    private static final long serialVersionUID = -5534226940936358917L;

    private final String eventId;
    private final String listenerType;

    public EventHandleException(String eventId, String message) {
        super(message);
        this.eventId = eventId;
        this.listenerType = null;
    }

    public EventHandleException(String eventId, String message, Throwable cause) {
        super(message, cause);
        this.eventId = eventId;
        this.listenerType = null;
    }

    public EventHandleException(String eventId, String listenerType, String message, Throwable cause) {
        super(message, cause);
        this.eventId = eventId;
        this.listenerType = listenerType;
    }
}
