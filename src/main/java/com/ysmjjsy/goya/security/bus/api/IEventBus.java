package com.ysmjjsy.goya.security.bus.api;

import com.ysmjjsy.goya.security.bus.core.MessageConfigHint;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 统一事件总线接口 - 负责消息的发布和分发
 * 采用 Hint 模式，通过智能决策引擎动态选择最优的消息配置
 *
 * @author goya
 * @since 2025/6/24
 */
public interface IEventBus {

    /**
     * 发布事件 - 使用框架默认配置
     *
     * @param event 要发布的事件
     * @return 发布结果
     */
    PublishResult publish(IEvent event);

    /**
     * 发布事件 - 核心方法，使用配置提示
     *
     * @param event 要发布的事件
     * @param hint  消息配置提示，用于指导智能决策引擎
     * @return 发布结果
     */
    PublishResult publish(IEvent event, MessageConfigHint hint);

    /**
     * 发布延迟消息 - 便捷方法
     *
     * @param event 要发布的事件
     * @param delay 延迟时长
     * @return 发布结果
     */
    PublishResult publishDelayed(IEvent event, Duration delay);

    /**
     * 发布定时消息 - 便捷方法
     *
     * @param event       要发布的事件
     * @param deliverTime 投递时间
     * @return 发布结果
     */
    PublishResult publishScheduled(IEvent event, LocalDateTime deliverTime);

    /**
     * 发布顺序消息 - 便捷方法
     *
     * @param event       要发布的事件
     * @param sequenceKey 顺序消息键，相同键的消息将按顺序消费
     * @return 发布结果
     */
    PublishResult publishOrdered(IEvent event, String sequenceKey);

    /**
     * 发布事务消息 - 便捷方法
     * 与当前Spring事务绑定，事务提交时消息才会真正发送
     *
     * @param event 要发布的事件
     * @return 发布结果
     */
    PublishResult publishTransactional(IEvent event);
} 