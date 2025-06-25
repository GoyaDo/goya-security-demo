package com.ysmjjsy.goya.security.bus.bus;

import com.ysmjjsy.goya.security.bus.domain.EventPublishResult;
import com.ysmjjsy.goya.security.bus.domain.IEvent;
import com.ysmjjsy.goya.security.bus.processor.MethodIEventListenerWrapper;
import org.springframework.transaction.event.TransactionPhase;

import java.util.List;
import java.util.Map;

/**
 * <p></p>
 *
 * @author goya
 * @since 2025/6/24 16:39
 */
public interface IEventBus {

    Map<Class<? extends IEvent<?>>, List<MethodIEventListenerWrapper>> getRegisterEventListeners();

    /**
     * 发布事件
     *
     * @param event 事件
     * @return 发布结果
     */
    default EventPublishResult publish(IEvent<?> event) {
        return publish(event, false, null);
    }

    /**
     * 发布事件
     *
     * @param event 事件
     * @param async 是否异步
     * @param phase 事物
     * @return 发布结果
     */
    EventPublishResult publish(IEvent<?> event, boolean async, TransactionPhase phase);

    /**
     * 事务性发布事件
     * 事件将在当前事务提交后发布
     *
     * @param event 要发布的事件
     * @return 发布结果
     */
    default EventPublishResult publishTransactional(IEvent<?> event) {
        return publish(event, false, TransactionPhase.AFTER_COMMIT);
    }

    /**
     * 事务性发布事件
     * 事件将在当前事务提交后发布
     *
     * @param event 要发布的事件
     * @param phase 事物阶段
     * @return 发布结果
     */
    default EventPublishResult publishTransactional(IEvent<?> event, TransactionPhase phase) {
        return publish(event, false, phase);
    }

    /**
     * 异步发布事件
     *
     * @param event 要发布的事件
     * @return 异步发布结果
     */
    default EventPublishResult publishAsync(IEvent<?> event) {
        return publish(event, true, null);
    }

    /**
     * 异步发布事件
     *
     * @param event 要发布的事件
     * @return 异步发布结果
     */
    default EventPublishResult publishAsyncTransactional(IEvent<?> event) {
        return publish(event, true, TransactionPhase.AFTER_COMMIT);
    }

    /**
     * 异步发布事件
     *
     * @param event 要发布的事件
     * @param phase 事物阶段
     * @return 异步发布结果
     */
    default EventPublishResult publishAsyncTransactional(IEvent<?> event, TransactionPhase phase) {
        return publish(event, true, phase);
    }

    /**
     * 注册事件监听器
     *
     * @param listener  事件监听器
     * @param eventType 监听的事件类型
     */
    <E extends IEvent<E>> void subscribe(MethodIEventListenerWrapper listener, Class<E> eventType);

    /**
     * 取消注册事件监听器
     *
     * @param listener  事件监听器
     * @param eventType 监听的事件类型
     */
    <E extends IEvent<E>> void unsubscribe(MethodIEventListenerWrapper listener, Class<E> eventType);

}
