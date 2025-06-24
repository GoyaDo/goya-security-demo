package com.ysmjjsy.goya.security.bus.domain;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 16:41
 */
@Getter
public class EventPublishResult {

    /**
     * 事件ID
     */
    private final String eventId;

    /**
     * 是否发布成功
     */
    private final boolean success;

    /**
     * 发布时间
     */
    private final LocalDateTime publishTime;

    /**
     * 错误原因
     */
    private final String errorReason;

    private EventPublishResult(Builder builder) {
        this.eventId = builder.eventId;
        this.success = builder.success;
        this.publishTime = builder.publishTime;
        this.errorReason = builder.errorReason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EventPublishResult success(String eventId) {
        return builder()
                .eventId(eventId)
                .success(true)
                .publishTime(LocalDateTime.now())
                .build();
    }

    public static EventPublishResult failure(String eventId, String error) {
        return builder()
                .eventId(eventId)
                .success(false)
                .publishTime(LocalDateTime.now())
                .addError(error)
                .build();
    }

    public static class Builder {
        private String eventId;
        private boolean success = false;
        private LocalDateTime publishTime = LocalDateTime.now();
        private String errorReason;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder publishTime(LocalDateTime publishTime) {
            this.publishTime = publishTime;
            return this;
        }

        public Builder addError(String errorReason) {
            this.errorReason = errorReason;
            return this;
        }

        public EventPublishResult build() {
            return new EventPublishResult(this);
        }
    }
}
