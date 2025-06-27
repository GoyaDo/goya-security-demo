package com.ysmjjsy.goya.security.bus.api;

import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;

/**
 * 统一事件监听器接口
 * 
 * 事件监听器需要实现此接口来处理接收到的消息
 * 框架会使用 Spring 的 GenericTypeResolver 安全地解析泛型类型 T
 *
 * @param <E> 监听的事件类型，必须继承 IEvent
 * @author goya
 * @since 2025/6/24
 */
public interface IEventListener<E extends IEvent> {

    /**
     * 处理接收到的事件
     *
     * @param event 接收到的事件
     * @return 消费结果，指示消息的处理状态
     */
    ConsumeResult onEvent(E event);


} 