package com.ysmjjsy.goya.security.bus.bus;

import cn.hutool.core.collection.IterUtil;
import com.ysmjjsy.goya.security.bus.domain.*;
import com.ysmjjsy.goya.security.bus.exception.EventHandleException;
import com.ysmjjsy.goya.security.bus.processor.MethodIEventListenerWrapper;
import com.ysmjjsy.goya.security.bus.route.EventRouter;
import com.ysmjjsy.goya.security.bus.route.EventRoutingDecision;
import com.ysmjjsy.goya.security.bus.transport.EventTransport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * <p>抽象事件总线定义</p>
 *
 * @author goya
 * @since 2025/6/24 20:29
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractIEventBus implements IEventBus {

    protected final ApplicationEventPublisher applicationEventPublisher;

    protected final List<EventTransport> eventTransports;
    protected final EventRouter eventRouter;

    @Getter
    protected final Map<Class<? extends IEvent>, List<MethodIEventListenerWrapper>> registerEventListeners = new ConcurrentHashMap<>();

    @Override
    public EventPublishResult publish(IEvent event, boolean isAsync, TransactionPhase phase) {
        log.info("publish event: {}", event);

        if (Objects.isNull(event)) {
            return EventPublishResult.failure("", "event is null");
        }
        EventPublishResult.Builder resultBuilder = EventPublishResult
                .builder()
                .eventId(event.getEventId());

        try {
            EventRoutingDecision route = eventRouter.route(event);
            event.topic(route.getTopic());
            // 本地处理
            resultBuilder = localPublish(event, route, resultBuilder, isAsync, phase);
        } catch (Exception e) {
            log.error("publish event error", e);
            resultBuilder.success(false).publishTime(LocalDateTime.now()).addError(e.getMessage());
        }

        return resultBuilder.build();
    }


    @Override
    public <E extends IEvent> void subscribe(MethodIEventListenerWrapper listener, Class<E> eventType) {
        registerEventListeners
                .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        log.info("Registered local listener for event type {}: {}",
                eventType.getSimpleName(), listener);
    }

    @Override
    public <E extends IEvent> void unsubscribe(MethodIEventListenerWrapper listener, Class<E> eventType) {
        List<MethodIEventListenerWrapper> listeners = registerEventListeners.get(eventType);
        if (IterUtil.isNotEmpty(listeners)) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                registerEventListeners.remove(eventType);
            }
        }
        log.info("Unregistered local listener for event type: {}", eventType.getSimpleName());
    }

    /**
     * 远程广播
     *
     * @param event         事件
     * @param route         路由信息
     * @param resultBuilder 结果
     * @param async         是否异步
     * @param phase         事物
     * @return 结果
     */
    private EventPublishResult.Builder localPublish(IEvent event, EventRoutingDecision route, EventPublishResult.Builder resultBuilder, boolean async, TransactionPhase phase) {
        if (Objects.nonNull(phase)) {
            return localPublishTransactional(event, route, resultBuilder, async, phase);
        }

        if (async) {
            IEventAsyncSpringEvent springEvent = new IEventAsyncSpringEvent(this, event, route);
            applicationEventPublisher.publishEvent(springEvent);
            resultBuilder.success(true).publishTime(LocalDateTime.now());
        } else {
            IEventDefaultSpringEvent springEvent = new IEventDefaultSpringEvent(this, event, route);
            applicationEventPublisher.publishEvent(springEvent);
            resultBuilder.success(true).publishTime(LocalDateTime.now());
        }
        return resultBuilder;
    }

    private EventPublishResult.Builder localPublishTransactional(IEvent event, EventRoutingDecision route, EventPublishResult.Builder resultBuilder, boolean async, TransactionPhase phase) {
        switch (phase) {
            case BEFORE_COMMIT: {
                IEventTransBeforeSpringEvent springEvent = new IEventTransBeforeSpringEvent(this, event, route);
                applicationEventPublisher.publishEvent(springEvent);
                resultBuilder.success(true).publishTime(LocalDateTime.now());
                break;
            }
            case AFTER_COMMIT: {
                if (!async) {
                    IEventTransSpringEvent springEvent = new IEventTransSpringEvent(this, event, route);
                    applicationEventPublisher.publishEvent(springEvent);
                } else {
                    IEventAsyncTransSpringEvent springEvent = new IEventAsyncTransSpringEvent(this, event, route);
                    applicationEventPublisher.publishEvent(springEvent);
                }
                resultBuilder.success(true).publishTime(LocalDateTime.now());
                break;
            }
            case AFTER_ROLLBACK: {
                IEventTransRollbackSpringEvent springEvent = new IEventTransRollbackSpringEvent(this, event, route);
                applicationEventPublisher.publishEvent(springEvent);
                resultBuilder.success(true).publishTime(LocalDateTime.now());
                break;
            }
            case AFTER_COMPLETION: {
                IEventTransCompletionSpringEvent springEvent = new IEventTransCompletionSpringEvent(this, event, route);
                applicationEventPublisher.publishEvent(springEvent);
                resultBuilder.success(true).publishTime(LocalDateTime.now());
                break;
            }
            default: {
                resultBuilder.success(false).publishTime(LocalDateTime.now()).addError("not support phase: " + phase);
                break;
            }
        }
        return resultBuilder;
    }

    @Component
    @RequiredArgsConstructor
    public static class SpringApplicationEventListener {

        private final IEventBus eventBus;
        protected final List<EventTransport> eventTransports;

        @EventListener(IEventDefaultSpringEvent.class)
        public void onApplicationEvent(IEventDefaultSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        @Async
        @EventListener(IEventAsyncSpringEvent.class)
        public void onApplicationEvent(IEventAsyncSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        @TransactionalEventListener(value = IEventTransSpringEvent.class, phase = TransactionPhase.AFTER_COMMIT)
        public void onApplicationEvent(IEventTransSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        @TransactionalEventListener(value = IEventTransBeforeSpringEvent.class, phase = TransactionPhase.BEFORE_COMMIT)
        public void onApplicationEvent(IEventTransBeforeSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        @TransactionalEventListener(value = IEventTransRollbackSpringEvent.class, phase = TransactionPhase.AFTER_ROLLBACK)
        public void onApplicationEvent(IEventTransRollbackSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        @TransactionalEventListener(value = IEventTransCompletionSpringEvent.class, phase = TransactionPhase.AFTER_COMPLETION)
        public void onApplicationEvent(IEventTransCompletionSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        @Async
        @TransactionalEventListener(value = IEventAsyncTransSpringEvent.class, phase = TransactionPhase.AFTER_COMMIT)
        public void onApplicationEvent(IEventAsyncTransSpringEvent iEventSpringEvent) {
            publishEvent(iEventSpringEvent.getIEvent(), iEventSpringEvent.getEventRoutingDecision());
        }

        private void publishEvent(IEvent iEvent, EventRoutingDecision eventRoutingDecision) {
            List<MethodIEventListenerWrapper> iEventListeners = eventBus.getRegisterEventListeners().get(iEvent.getClass());
            if (IterUtil.isEmpty(iEventListeners)) {
                log.warn("Registered local listener for event type: {},listener is empty", iEvent.getClass().getSimpleName());
            }

            List<MethodIEventListenerWrapper> listenerWrappers = iEventListeners
                    .stream()
                    .filter(l -> l.checkRules(iEvent, eventRoutingDecision.getTopic()))
                    .collect(Collectors.toList());

            if (eventRoutingDecision.shouldPublishLocal()) {
                listenerWrappers.forEach(listener -> {
                    try {
                        listener.onEvent(iEvent);
                    } catch (EventHandleException e) {
                        log.error("Failed to process event in method listener", e);
                    }
                });
            }

            if (eventRoutingDecision.shouldPublishRemote()) {
                listenerWrappers.forEach(listener -> {
                    try {
                        eventTransports.stream()
                                .filter(t -> t.getTransportType().equals(eventRoutingDecision.getRemoteType()))
                                .forEach(eventTransport -> {
                            try {
                                eventTransport.send(iEvent);
                            } catch (Exception e) {
                                log.error("Failed to publish event to transport: {}", eventTransport.getTransportType(), e);
                            }
                        });

                    } catch (EventHandleException e) {
                        log.error("Failed to process event in method listener", e);
                    }
                });
            }

        }
    }
}
