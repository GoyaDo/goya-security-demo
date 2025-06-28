package com.ysmjjsy.goya.security.bus.core;

import com.ysmjjsy.goya.security.bus.api.IEvent;
import com.ysmjjsy.goya.security.bus.api.IEventListener;
import com.ysmjjsy.goya.security.bus.decision.DecisionResult;
import com.ysmjjsy.goya.security.bus.enums.ConsumeResult;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地事件总线
 * <p>
 * 用于JVM内部的事件发布和订阅，支持同步和异步处理
 * 不依赖外部MQ，实现进程内的事件传递
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
public class LocalEventBus {

    /**
     * 事件类型 -> 监听器列表的映射
     */
    private final Map<String, List<ListenerWrapper>> listeners = new ConcurrentHashMap<>();

    /**
     * 注册监听器
     *
     * @param eventKey 事件类型
     * @param listener  监听器
     */
    public void registerListener(String eventKey, IEventListener<? extends IEvent> listener) {
        listeners.computeIfAbsent(eventKey, k -> new CopyOnWriteArrayList<>())
                .add(new ListenerWrapper(listener));
        log.info("Registered local listener for event type: {}", eventKey);
    }

    /**
     * 注册监听器（带配置）
     *
     * @param eventKey 事件类型
     * @param listener  监听器
     * @param config    监听器配置
     */
    public void registerListener(String eventKey, IEventListener<? extends IEvent> listener, ListenerConfig config) {
        listeners.computeIfAbsent(eventKey, k -> new CopyOnWriteArrayList<>())
                .add(new ListenerWrapper(listener, config));
        log.info("Registered local listener for event type: {} with config: {}", eventKey, config);
    }

    /**
     * 移除监听器
     *
     * @param eventKey 事件类型
     * @param listener  监听器
     */
    public void removeListener(String eventKey, IEventListener<? extends IEvent> listener) {
        List<ListenerWrapper> listenerList = listeners.get(eventKey);
        if (listenerList != null) {
            listenerList.removeIf(wrapper -> wrapper.getListener() == listener);
            if (listenerList.isEmpty()) {
                listeners.remove(eventKey);
            }
            log.info("Removed local listener for event type: {}", eventKey);
        }
    }

    /**
     * 发布事件
     *
     * @param event    事件
     * @param decision
     * @return 是否成功发布（至少有一个监听器处理）
     */
    @SuppressWarnings("unchecked")
    public boolean publish(IEvent event, DecisionResult decision) {
        String eventKey = event.getEventKey();
        List<ListenerWrapper> listenerList = listeners.get(eventKey);

        if (listenerList == null || listenerList.isEmpty()) {
            log.debug("No local listeners found for event type: {}", eventKey);
            return false;
        }

        boolean hasSuccessfulExecution = false;

        for (ListenerWrapper wrapper : listenerList) {
            try {
                IEventListener<IEvent> listener = (IEventListener<IEvent>) wrapper.getListener();
                ConsumeResult result = listener.onEvent(event);

                if (result == ConsumeResult.SUCCESS) {
                    hasSuccessfulExecution = true;
                    log.debug("Local listener successfully processed event: {}", event.getEventId());
                } else {
                    log.warn("Local listener failed to process event: {}, result: {}", event.getEventId(), result);
                }

            } catch (Exception e) {
                log.error("Local listener threw exception while processing event: {}", event.getEventId(), e);
            }
        }

        return hasSuccessfulExecution;
    }

    /**
     * 检查是否有监听器
     *
     * @param eventKey 事件类型
     * @return 是否有监听器
     */
    public boolean hasListeners(String eventKey) {
        List<ListenerWrapper> listenerList = listeners.get(eventKey);
        return listenerList != null && !listenerList.isEmpty();
    }

    /**
     * 获取监听器数量
     *
     * @param eventKey 事件类型
     * @return 监听器数量
     */
    public int getListenerCount(String eventKey) {
        List<ListenerWrapper> listenerList = listeners.get(eventKey);
        return listenerList != null ? listenerList.size() : 0;
    }

    /**
     * 获取所有注册的事件类型
     *
     * @return 事件类型集合
     */
    public java.util.Set<String> getRegisteredEventTypes() {
        return listeners.keySet();
    }

    /**
     * 清空所有监听器
     */
    public void clear() {
        listeners.clear();
        log.info("Cleared all local listeners");
    }

    /**
     * 监听器包装类
     */
    private static class ListenerWrapper {
        private final IEventListener<? extends IEvent> listener;
        private final ListenerConfig config;

        public ListenerWrapper(IEventListener<? extends IEvent> listener) {
            this(listener, ListenerConfig.defaultConfig());
        }

        public ListenerWrapper(IEventListener<? extends IEvent> listener, ListenerConfig config) {
            this.listener = listener;
            this.config = config;
        }

        public IEventListener<? extends IEvent> getListener() {
            return listener;
        }

        public ListenerConfig getConfig() {
            return config;
        }
    }

    /**
     * 监听器配置
     */
    @lombok.Data
    @Builder
    public static class ListenerConfig {
        @Builder.Default
        private boolean async = false;
        @Builder.Default
        private int retryTimes = 0;
        @Builder.Default
        private long timeout = 5000L; // 超时时间（毫秒）

        public static ListenerConfig defaultConfig() {
            return ListenerConfig.builder().build();
        }
    }
} 