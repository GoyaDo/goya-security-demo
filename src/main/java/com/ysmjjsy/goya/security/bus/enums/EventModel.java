package com.ysmjjsy.goya.security.bus.enums;

/**
 * <p>事件模型枚举</p>
 *
 * @author goya
 * @since 2025/6/26 21:59
 */
public enum EventModel {

    /**
     * 队列模式 - 点对点，每条事件只被一个消费者处理
     */
    QUEUE,

    /**
     * 广播模式 - 广播，每条事件被所有消费者处理
     */
    BROADCAST,

    /**
     * 主题模式 - 发布订阅，每条事件可被多个订阅者处理
     */
    TOPIC
}
