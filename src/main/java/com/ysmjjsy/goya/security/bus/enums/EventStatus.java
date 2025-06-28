package com.ysmjjsy.goya.security.bus.enums;

/**
 * 消息状态枚举
 * 
 * 用于Outbox模式中跟踪消息的生命周期状态
 *
 * @author goya
 * @since 2025/6/24
 */
public enum EventStatus {

    /**
     * 待发送 - 消息已保存，等待发送
     */
    PENDING("待发送"),

    /**
     * 发送中 - 消息正在发送
     */
    SENDING("发送中"),

    /**
     * 发送成功 - 消息已成功发送到MQ
     */
    SUCCESS("发送成功"),

    /**
     * 发送失败 - 消息发送失败，等待重试
     */
    FAILED("发送失败"),

    /**
     * 重试中 - 消息正在重试发送
     */
    RETRYING("重试中"),

    /**
     * 死信 - 消息重试次数已达上限，进入死信状态
     */
    DEAD_LETTER("死信"),

    /**
     * 已过期 - 消息TTL过期
     */
    EXPIRED("已过期"),

    /**
     * 已取消 - 消息被手动取消
     */
    CANCELLED("已取消");

    private final String description;

    EventStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 是否为终态
     * 终态的消息不会再被处理
     *
     * @return true如果是终态
     */
    public boolean isFinalState() {
        return this == SUCCESS || this == DEAD_LETTER || this == EXPIRED || this == CANCELLED;
    }

    /**
     * 是否可重试
     *
     * @return true如果可重试
     */
    public boolean isRetryable() {
        return this == FAILED;
    }

    /**
     * 是否正在处理中
     *
     * @return true如果正在处理
     */
    public boolean isProcessing() {
        return this == SENDING || this == RETRYING;
    }
} 