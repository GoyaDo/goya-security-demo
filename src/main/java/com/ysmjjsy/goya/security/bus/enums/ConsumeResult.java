package com.ysmjjsy.goya.security.bus.enums;

/**
 * 消费结果枚举
 * 
 * 指示消息的处理状态，用于决定后续的处理策略
 *
 * @author goya
 * @since 2025/6/24
 */
public enum ConsumeResult {
    /**
     * 消息成功处理
     */
    SUCCESS,
    
    /**
     * 消息处理失败，需要重试
     * 框架将根据配置的重试策略进行重试
     */
    RETRY,
    
    /**
     * 消息处理失败，且不应重试
     * 消息将直接进入死信队列
     */
    DEAD_LETTER
} 