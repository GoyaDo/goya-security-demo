package com.ysmjjsy.goya.security.bus.exception;

/**
 * <p>事件异常</p>
 *
 * @author goya
 * @since 2025/6/24 17:13
 */
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

    @Override
    public String toString() {
        return String.format("EventHandleException{eventId='%s', listenerType='%s', message='%s'}",
                eventId, listenerType, getMessage());
    }
}
