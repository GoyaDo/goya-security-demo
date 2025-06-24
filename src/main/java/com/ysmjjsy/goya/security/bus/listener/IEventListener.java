package com.ysmjjsy.goya.security.bus.listener;


import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;

/**
 * <p>统一事件监听器接口</p>
 *
 * @param <E> 监听的事件类型
 *
 * @author goya
 * @since 2025/6/24 17:13
 */
public interface IEventListener<E extends IEvent> {

    /**
     * 处理事件
     *
     * @param event 接收到的事件
     * @throws EventHandleException 事件处理异常
     */
    void onEvent(E event) throws EventHandleException;

    /**
     * 获取事件主题
     *
     * @return 事件主题
     */
    String topic();
}
