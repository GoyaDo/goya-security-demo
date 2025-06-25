package com.ysmjjsy.goya.security.bus.transport;

import com.ysmjjsy.goya.security.bus.enums.BusRemoteType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 传输结果
 *
 * @author goya
 * @since 2025/6/13 17:56
 */
@Getter
public class TransportResult {

    /**
     * 是否成功
     */
    private final boolean success;

    /**
     * 消息
     */
    private final String message;

    /**
     * 传输类型
     */
    private final BusRemoteType transportType;

    /**
     * 主题
     */
    private final String topic;

    /**
     * 事件ID
     */
    private final String eventId;

    /**
     * 发送时间
     */
    private final LocalDateTime sendTime;

    /**
     * 错误
     */
    private final Throwable error;

    private TransportResult(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.transportType = builder.transportType;
        this.topic = builder.topic;
        this.eventId = builder.eventId;
        this.sendTime = builder.sendTime;
        this.error = builder.error;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TransportResult success(BusRemoteType transportType, String topic, String eventId) {
        return builder()
                .success(true)
                .transportType(transportType)
                .topic(topic)
                .eventId(eventId)
                .sendTime(LocalDateTime.now())
                .message("Event sent successfully")
                .build();
    }

    public static TransportResult failure(BusRemoteType transportType, String topic, String eventId, Throwable error) {
        return builder()
                .success(false)
                .transportType(transportType)
                .topic(topic)
                .eventId(eventId)
                .sendTime(LocalDateTime.now())
                .error(error)
                .message("Failed to send event: " + error.getMessage())
                .build();
    }

    public static class Builder {
        private boolean success;
        private String message;
        private BusRemoteType transportType;
        private String topic;
        private String eventId;
        private LocalDateTime sendTime;
        private Throwable error;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder transportType(BusRemoteType transportType) {
            this.transportType = transportType;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder sendTime(LocalDateTime sendTime) {
            this.sendTime = sendTime;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public TransportResult build() {
            return new TransportResult(this);
        }
    }
}